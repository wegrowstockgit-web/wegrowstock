package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Location;
import com.invsys.domain.OutboxEvent;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.TaxScheme;
import com.invsys.domain.TaxSchemeRate;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.OutboxEventRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.TaxSchemeRateRepository;
import com.invsys.repository.TaxSchemeRepository;
import com.invsys.common.ApiException;
import com.invsys.integration.outbox.CostingMethodChangedHandler;
import com.invsys.service.DataIngestionService;
import com.invsys.service.InventoryService;
import com.invsys.service.SettingsService;
import com.invsys.service.SkuMaskService;
import com.invsys.service.StackingTaxEngine;
import com.invsys.service.TaxSchemeService;
import com.invsys.service.ValuationRecostService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EnterpriseIngestionTaxArchitectureTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TaxSchemeRepository taxSchemeRepository;
    @Autowired TaxSchemeRateRepository taxSchemeRateRepository;
    @Autowired StackingTaxEngine stackingTaxEngine;
    @Autowired SkuMaskService skuMaskService;
    @Autowired SettingsService settingsService;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired InventoryService inventoryService;
    @Autowired ValuationRecostService valuationRecostService;
    @Autowired DataIngestionService dataIngestionService;
    @Autowired TaxSchemeService taxSchemeService;
    @Autowired CostingMethodChangedHandler costingMethodChangedHandler;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void stackingTaxSumsLinearRatesWithMinorUnitRounding() {
        UUID tenantId = signupTenant("tax-stack");
        TenantContext.setTenantId(tenantId);

        TaxScheme scheme = new TaxScheme();
        scheme.setTenantId(tenantId);
        scheme.setName("GST+PST");
        scheme.setTaxInclusive(false);
        scheme = taxSchemeRepository.save(scheme);

        TaxSchemeRate primary = new TaxSchemeRate();
        primary.setTenantId(tenantId);
        primary.setTaxSchemeId(scheme.getId());
        primary.setName("GST");
        primary.setRate(new BigDecimal("0.0500"));
        primary.setSortOrder(0);
        taxSchemeRateRepository.save(primary);

        TaxSchemeRate secondary = new TaxSchemeRate();
        secondary.setTenantId(tenantId);
        secondary.setTaxSchemeId(scheme.getId());
        secondary.setName("PST");
        secondary.setRate(new BigDecimal("0.0700"));
        secondary.setSortOrder(1);
        taxSchemeRateRepository.save(secondary);

        // P=10.00, Q=3 → base 30; tax = 1.50 + 2.10 = 3.60
        var result = stackingTaxEngine.compute(scheme.getId(), new BigDecimal("10.00"), new BigDecimal("3"));
        assertThat(result.totalTax()).isEqualByComparingTo("3.60");
        assertThat(result.grandTotal()).isEqualByComparingTo("33.60");
        assertThat(result.lines()).hasSize(2);
    }

    @Test
    void skuMaskMintsUniqueSkuWhenNull() {
        UUID tenantId = signupTenant("sku-mask");
        TenantContext.setTenantId(tenantId);

        settingsService.patchSettings(Map.of(
                "sku_template", "SKU-{PREFIX}-{ID:5}",
                "sku_prefix", "ACME"));

        String first = skuMaskService.mintSku(null, null);
        String second = skuMaskService.mintSku(null, null);

        assertThat(first).startsWith("SKU-ACME-");
        assertThat(second).startsWith("SKU-ACME-");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void costingMethodChangeAppendsOutboxAndRecostUpdatesAvgCost() {
        UUID tenantId = signupTenant("recost");
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("RC");
        product.setName("Recost item");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("RC-1");
        variant.setAvgCost(BigDecimal.ZERO);
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-RC");
        wh.setName("Recost WH");
        wh.setPath("WH-RC");
        wh = locationRepository.save(wh);

        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("10"),
                "TEST", null, new BigDecimal("5.00"));
        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("10"),
                "TEST", null, new BigDecimal("15.00"));

        settingsService.patchSettings(Map.of("costing_method", "FIFO"));
        List<OutboxEvent> events = outboxEventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(e -> "COSTING_METHOD_CHANGED".equals(e.getEventType()))
                .toList();
        assertThat(events).isNotEmpty();

        valuationRecostService.recostTenant(tenantId);
        ProductVariant refreshed = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(refreshed.getAvgCost()).isEqualByComparingTo("10.0000");
    }

    @Test
    void ingestionImportStreamsCsvIntoLedger() throws Exception {
        String slug = "ingest-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Ingest Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-IN");
        wh.setName("Import WH");
        wh.setPath("WH-IN");
        wh = locationRepository.save(wh);

        String csv = "sku,name,qty,unitCost\nIMP-1,Widget,5,2.50\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "stock.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/ingestion/import")
                        .file(file)
                        .param("columnsMapping",
                                "{\"sku\":\"sku\",\"name\":\"name\",\"qty\":\"qty\",\"unitCost\":\"unitCost\"}")
                        .param("locationId", wh.getId().toString())
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        // HTTP filter clears ThreadLocal tenant; restore for repository assertions under RLS.
        TenantContext.setTenantId(tenantId);
        assertThat(variantRepository.findByTenantIdAndSku(tenantId, "IMP-1")).isPresent();
        assertThat(ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtAsc(
                tenantId,
                variantRepository.findByTenantIdAndSku(tenantId, "IMP-1").orElseThrow().getId()))
                .isNotEmpty();
    }

    @Test
    void stackingTaxInclusivePricingKeepsGrandTotalAtBase() {
        UUID tenantId = signupTenant("tax-incl");
        TenantContext.setTenantId(tenantId);

        TaxScheme scheme = new TaxScheme();
        scheme.setTenantId(tenantId);
        scheme.setName("Inclusive VAT");
        scheme.setTaxInclusive(true);
        scheme = taxSchemeRepository.save(scheme);

        TaxSchemeRate rate = new TaxSchemeRate();
        rate.setTenantId(tenantId);
        rate.setTaxSchemeId(scheme.getId());
        rate.setName("VAT");
        rate.setRate(new BigDecimal("0.2000"));
        rate.setSortOrder(0);
        taxSchemeRateRepository.save(rate);

        var result = stackingTaxEngine.compute(scheme.getId(), new BigDecimal("100.00"), BigDecimal.ONE);
        assertThat(result.totalTax()).isEqualByComparingTo("20.00");
        assertThat(result.grandTotal()).isEqualByComparingTo("100.00");
        assertThat(result.taxableBase()).isEqualByComparingTo("80.00");
    }

    @Test
    void taxSchemeServiceListsUpdatesAndPreviews() {
        UUID tenantId = signupTenant("tax-svc");
        TenantContext.setTenantId(tenantId);

        var created = taxSchemeService.create("Stacked", false, List.of(
                new TaxSchemeService.RateInput("Primary", new BigDecimal("0.05"), 0),
                new TaxSchemeService.RateInput("Secondary", new BigDecimal("0.03"), 1)));
        assertThat(taxSchemeService.list()).extracting(TaxSchemeService.SchemeView::name)
                .contains("Stacked");

        var updated = taxSchemeService.update(created.id(), "Stacked CA", true, true, List.of(
                new TaxSchemeService.RateInput("GST", new BigDecimal("0.05"), 0)));
        assertThat(updated.name()).isEqualTo("Stacked CA");
        assertThat(updated.taxInclusive()).isTrue();
        assertThat(updated.rates()).hasSize(1);

        var preview = taxSchemeService.preview(updated.id(), new BigDecimal("50"), new BigDecimal("2"));
        assertThat(preview.totalTax()).isEqualByComparingTo("5.00");
    }

    @Test
    void skuMaskRejectsCollisionAndMintsBarcode() {
        UUID tenantId = signupTenant("sku-col");
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("COL");
        product.setName("Collision");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("EXISTING-SKU");
        variantRepository.save(variant);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.invsys.common.ApiException.class,
                () -> skuMaskService.mintSku("EXISTING-SKU", null));

        settingsService.patchSettings(Map.of("barcode_template", "BC-{ID:6}", "sku_prefix", "X"));
        String barcode = skuMaskService.mintBarcode(null);
        assertThat(barcode).startsWith("BC-");
        assertThat(skuMaskService.mintBarcode("EXPLICIT-BC")).isEqualTo("EXPLICIT-BC");
    }

    @Test
    void variantCreateMintsSkuWhenOmitted() throws Exception {
        String slug = "varmint-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Variant Mint Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("VM");
        product.setName("Mintable");
        product = productRepository.save(product);

        String body = """
                {"productId":"%s","price":1.00,"currency":"USD"}
                """.formatted(product.getId());

        String response = mockMvc.perform(post("/api/v1/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(response).path("sku").asString()).isNotBlank();
    }

    @Test
    void costingMethodChangedHandlerRecostsMovingAverage() {
        UUID tenantId = signupTenant("ma-recost");
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("MA");
        product.setName("MA item");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("MA-1");
        variant.setAvgCost(BigDecimal.ZERO);
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-MA");
        wh.setName("MA WH");
        wh.setPath("WH-MA");
        wh = locationRepository.save(wh);

        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("10"),
                "TEST", null, new BigDecimal("4.00"));
        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("10"),
                "TEST", null, new BigDecimal("8.00"));

        settingsService.patchSettings(Map.of("costing_method", "MOVING_AVERAGE"));
        assertThat(costingMethodChangedHandler.eventType()).isEqualTo("COSTING_METHOD_CHANGED");
        costingMethodChangedHandler.handle(tenantId, tenantId, "COSTING_METHOD_CHANGED", Map.of());

        TenantContext.setTenantId(tenantId);
        ProductVariant refreshed = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(refreshed.getAvgCost()).isEqualByComparingTo("6.0000");
    }

    @Test
    void taxSchemeApiCreatesAndPreviews() throws Exception {
        String slug = "taxapi-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Tax API Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        String body = """
                {
                  "name": "Ontario",
                  "taxInclusive": false,
                  "rates": [
                    {"name": "GST", "rate": 0.05, "sortOrder": 0},
                    {"name": "PST", "rate": 0.08, "sortOrder": 1}
                  ]
                }
                """;

        String created = mockMvc.perform(post("/api/v1/settings/tax-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ontario"))
                .andExpect(jsonPath("$.rates.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String schemeId = objectMapper.readTree(created).path("id").asString();

        mockMvc.perform(post("/api/v1/settings/tax-schemes/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schemeId":"%s","unitPrice":100,"quantity":1}
                                """.formatted(schemeId))
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTax").value(13.0));

        mockMvc.perform(get("/api/v1/settings/tax-schemes")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ontario"));

        mockMvc.perform(put("/api/v1/settings/tax-schemes/" + schemeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ontario Updated","active":true,"taxInclusive":false,
                                 "rates":[{"name":"HST","rate":0.13,"sortOrder":0}]}
                                """)
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ontario Updated"))
                .andExpect(jsonPath("$.rates.length()").value(1));
    }

    @Test
    void ingestionSkipsBlankRowsAndReportsRowErrors() throws Exception {
        String slug = "ingest2-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse tokens = authService.signup(new SignupRequest(
                "Ingest2 Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-IN2");
        wh.setName("Import WH2");
        wh.setPath("WH-IN2");
        wh = locationRepository.save(wh);

        String csv = "sku,name,qty,unitCost\n\nBAD,Bad,-5,1.00\nOK-1,Good,1,1.00\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "stock.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/ingestion/import")
                        .file(file)
                        .param("columnsMapping",
                                "{\"sku\":\"sku\",\"name\":\"name\",\"qty\":\"qty\",\"unitCost\":\"unitCost\"}")
                        .param("locationId", wh.getId().toString())
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void ingestionServiceCoversQuotedCsvDefaultsAndExistingSku() {
        UUID tenantId = signupTenant("ingest3");
        TenantContext.setTenantId(tenantId);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-IN3");
        wh.setName("Import WH3");
        wh.setPath("WH-IN3");
        final UUID warehouseId = locationRepository.save(wh).getId();

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("EXIST");
        product.setName("Existing");
        product = productRepository.save(product);
        ProductVariant existing = new ProductVariant();
        existing.setTenantId(tenantId);
        existing.setProductId(product.getId());
        existing.setSku("EXIST-1");
        variantRepository.save(existing);

        String csv = """
                sku,name,barcode,qty,unitCost
                EXIST-1,"Quoted, Name",BC-1,0,1.25
                ,Name Only,,2,3.00
                ,,BC-ONLY,1,1.00
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "stock.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        // Null mapping → defaults; null locationId → first warehouse.
        DataIngestionService.ImportResult result = dataIngestionService.importFile(file, null, null);
        assertThat(result.imported()).isGreaterThanOrEqualTo(2);
        assertThat(result.skipped()).isGreaterThanOrEqualTo(1);
        assertThat(variantRepository.findByTenantIdAndSku(tenantId, "EXIST-1")).isPresent();

        assertThatThrownBy(() -> dataIngestionService.importFile(
                new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]),
                "{}", warehouseId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Upload file is required");

        assertThatThrownBy(() -> dataIngestionService.importFile(
                new MockMultipartFile("file", "bad.csv", "text/csv", "sku\nx\n".getBytes(StandardCharsets.UTF_8)),
                "not-json", warehouseId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("columnsMapping");

        assertThatThrownBy(() -> dataIngestionService.importFile(
                new MockMultipartFile("file", "blank.csv", "text/csv", new byte[0]),
                "{\"sku\":\"sku\"}", warehouseId))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fifoRecostConsumesLayersOnShipAndRestoresTenantContext() {
        UUID tenantId = signupTenant("fifo-ship");
        UUID otherTenant = signupTenant("fifo-other");
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("FF");
        product.setName("FIFO ship");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("FF-1");
        variant.setAvgCost(BigDecimal.ZERO);
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-FF");
        wh.setName("FIFO WH");
        wh.setPath("WH-FF");
        wh = locationRepository.save(wh);

        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("10"),
                "TEST", null, new BigDecimal("2.00"));
        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("10"),
                "TEST", null, new BigDecimal("8.00"));
        inventoryService.adjust(variant.getId(), wh.getId(), null, new BigDecimal("-5"), "TEST_SHIP");

        settingsService.patchSettings(Map.of("costing_method", "FIFO"));

        TenantContext.setTenantId(otherTenant);
        valuationRecostService.recostTenant(tenantId);
        // Previous tenant context restored after worker.
        assertThat(TenantContext.getTenantId()).contains(otherTenant);

        TenantContext.setTenantId(tenantId);
        ProductVariant refreshed = variantRepository.findById(variant.getId()).orElseThrow();
        // Remaining 15 units: 5@2 + 10@8 → weighted 6.0000
        assertThat(refreshed.getAvgCost()).isEqualByComparingTo("6.0000");
    }

    @Test
    void movingAverageRecostKeepsAvgAcrossOutboundAndScheduleRuns() {
        UUID tenantId = signupTenant("ma-ship");
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("MS");
        product.setName("MA ship");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("MS-1");
        variant.setAvgCost(BigDecimal.ZERO);
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-MS");
        wh.setName("MA Ship WH");
        wh.setPath("WH-MS");
        wh = locationRepository.save(wh);

        inventoryService.receive(variant.getId(), wh.getId(), null, new BigDecimal("10"),
                "TEST", null, new BigDecimal("4.00"));
        inventoryService.adjust(variant.getId(), wh.getId(), null, new BigDecimal("-3"), "TEST_SHIP");

        settingsService.patchSettings(Map.of("costing_method", "MOVING_AVERAGE"));
        valuationRecostService.scheduleRecost();
        valuationRecostService.recostTenant(tenantId);

        ProductVariant refreshed = variantRepository.findById(variant.getId()).orElseThrow();
        assertThat(refreshed.getAvgCost()).isEqualByComparingTo("4.0000");
    }

    @Test
    void stackingTaxEmptySchemeUsesNullSafeDefaults() {
        UUID tenantId = signupTenant("tax-empty");
        TenantContext.setTenantId(tenantId);

        StackingTaxEngine.TaxComputation empty = stackingTaxEngine.compute(null, new BigDecimal("10"), null);
        assertThat(empty.schemeId()).isNull();
        assertThat(empty.schemeName()).isNull();
        assertThat(empty.taxInclusive()).isFalse();
        assertThat(empty.unitPrice()).isEqualByComparingTo("10");
        assertThat(empty.quantity()).isEqualByComparingTo("1");
        assertThat(empty.taxableBase()).isEqualByComparingTo("10.00");
        assertThat(empty.totalTax()).isEqualByComparingTo("0.00");
        assertThat(empty.grandTotal()).isEqualByComparingTo("10.00");
        assertThat(empty.lines()).isEmpty();

        StackingTaxEngine.TaxComputation zeros = stackingTaxEngine.compute(
                null, null, BigDecimal.TEN);
        assertThat(zeros.unitPrice()).isEqualByComparingTo("0");
        assertThat(zeros.quantity()).isEqualByComparingTo("10");
        assertThat(zeros.grandTotal()).isEqualByComparingTo("0.00");
    }

    private UUID signupTenant(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return authService.signup(new SignupRequest(
                prefix + " Co", slug, "owner@" + slug + ".test", "password123", "Owner")).tenantId();
    }
}
