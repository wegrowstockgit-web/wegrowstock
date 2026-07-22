package com.invsys;

import com.invsys.api.dto.ImportRowStatus;
import com.invsys.api.dto.PreflightResponse;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.AuditLog;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.repository.AuditLogRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.service.DataIngestionService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DataIngestionPreflightTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired DataIngestionService dataIngestionService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void preflightFlagsMissingProductAndDimensionErrorsWithoutLedgerWrites() throws Exception {
        TokenResponse tokens = signup("pf-miss");
        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);
        Location wh = saveWarehouse(tenantId, "WH-PF");

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("KNOWN");
        product.setName("Known");
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("KNOWN-1");
        variantRepository.save(variant);

        String csv = """
                sku,name,qty,length,width,height,location_path
                KNOWN-1,Known,1,10,8,6,WH-PF
                NEW-1,NoDims,1,,,
                NEW-2,HasDims,2,12,10,8,WH-PF/Z-NEW/B-01
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "pf.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/ingestion/preflight")
                        .file(file)
                        .param("columnsMapping", mappingJson())
                        .param("locationId", wh.getId().toString())
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.fileChecksumSha256").isNotEmpty());

        TenantContext.setTenantId(tenantId);
        PreflightResponse pf = dataIngestionService.preflight(file, mappingJson(), wh.getId());
        assertThat(pf.rows()).hasSize(3);
        assertThat(pf.rows().get(0).status()).isEqualTo(ImportRowStatus.READY_TO_IMPORT);
        assertThat(pf.rows().get(1).status()).isEqualTo(ImportRowStatus.VALIDATION_ERROR);
        assertThat(pf.rows().get(2).status()).isEqualTo(ImportRowStatus.MISSING_PRODUCT);
        assertThat(pf.missingSkus()).contains("NEW-2");
        assertThat(pf.rows().get(2).detail()).contains("location_path not found");
        assertThat(variantRepository.findByTenantIdAndSku(tenantId, "NEW-2")).isEmpty();
    }

    @Test
    void createMissingProductsThenImportReadyRowsAndAuditChecksum() throws Exception {
        TokenResponse tokens = signup("pf-create");
        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);
        Location wh = saveWarehouse(tenantId, "WH-CR");

        String csv = """
                sku,name,qty,unitCost,length,width,height,location_path
                COLD-1,Cold Start,5,1.25,10,8,6,WH-CR
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "cold.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/ingestion/create-missing-products")
                        .file(file)
                        .param("columnsMapping", mappingJson())
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        TenantContext.setTenantId(tenantId);
        assertThat(variantRepository.findByTenantIdAndSku(tenantId, "COLD-1")).isPresent();

        mockMvc.perform(multipart("/api/v1/ingestion/import")
                        .file(file)
                        .param("columnsMapping", mappingJson())
                        .param("locationId", wh.getId().toString())
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.fileChecksumSha256").isNotEmpty());

        TenantContext.setTenantId(tenantId);
        assertThat(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .extracting(AuditLog::getAction)
                .contains("DATA_IMPORT");
        AuditLog entry = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(a -> "DATA_IMPORT".equals(a.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(entry.getDiff()).containsKey("checksumSha256");
        assertThat(entry.getDiff().get("checksumSha256")).isNotNull();
    }

    @Test
    void importCreatesLocationPathTreeWhenEnabled() {
        TokenResponse tokens = signup("pf-path");
        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(tokens.userId());
        // Signup provisions WH-01 (path may be /WH-01); resolve-or-create extends that tree.

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PATH");
        product.setName("Path SKU");
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PATH-1");
        variant.setLength(new java.math.BigDecimal("1"));
        variant.setWidth(new java.math.BigDecimal("1"));
        variant.setHeight(new java.math.BigDecimal("1"));
        variantRepository.save(variant);

        String csv = """
                sku,name,qty,location_path
                PATH-1,Path SKU,3,WH-01/ZoneA/Bin01
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "path.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        DataIngestionService.ImportResult result = dataIngestionService.importFile(
                file, mappingJson(), null,
                new DataIngestionService.ImportOptions(false, true));
        assertThat(result.imported()).isEqualTo(1);
        assertThat(locationRepository.findByTenantIdAndPath(tenantId, "WH-01/ZoneA/Bin01")).isPresent();
        assertThat(locationRepository.findByTenantIdAndPath(tenantId, "WH-01/ZoneA")).isPresent();
    }

    @Test
    void importSkipsMissingProductWithoutAutoCreate() {
        TokenResponse tokens = signup("pf-skip");
        UUID tenantId = tokens.tenantId();
        TenantContext.setTenantId(tenantId);
        saveWarehouse(tenantId, "WH-SK");

        String csv = """
                sku,name,qty,length,width,height
                GHOST-1,Ghost,1,10,8,6
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "ghost.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        DataIngestionService.ImportResult result = dataIngestionService.importFile(
                file, mappingJson(), null);
        assertThat(result.imported()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(variantRepository.findByTenantIdAndSku(tenantId, "GHOST-1")).isEmpty();
    }

    private TokenResponse signup(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return authService.signup(new SignupRequest(
                prefix + " Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
    }

    private Location saveWarehouse(UUID tenantId, String code) {
        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode(code);
        wh.setName(code);
        wh.setPath(code);
        return locationRepository.save(wh);
    }

    private static String mappingJson() {
        return """
                {"sku":"sku","name":"name","qty":"qty","unitCost":"unitCost",\
                "length":"length","width":"width","height":"height","locationPath":"location_path"}
                """;
    }
}
