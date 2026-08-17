package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.subscription.AppModule;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.service.InventoryLevelFlushWorker;
import com.invsys.service.TenantSubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PosSyncHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TenantSubscriptionService tenantSubscriptionService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired InventoryLevelRepository levelRepository;
    @Autowired InventoryLevelFlushWorker flushWorker;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void syncReceipts_queuesInventoryDeltasWithoutLockingLevelsThenFlushApplies() throws Exception {
        Fixture fx = seedCatalog();

        String receiptId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/pos/sync-receipts")
                        .header("Authorization", "Bearer " + fx.owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"%s","storeLocationId":"%s","tenderType":"CASH","taxRegion":"US",
                                  "lines":[{"variantId":"%s","upc":"7700222200099","quantity":2,"unitPrice":4.50}]}]
                                """.formatted(receiptId, fx.bin.getId(), fx.variant.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicates").value(0));

        TenantContext.setTenantId(fx.owner.tenantId());
        List<InventoryLedger> sales = ledgerRepository.findByTenantIdAndReferenceTypeAndReferenceId(
                fx.owner.tenantId(), "POS_RECEIPT", UUID.fromString(receiptId));
        assertThat(sales).hasSize(1);
        assertThat(sales.get(0).getReasonCode()).isEqualTo("POS_SALE");
        assertThat(sales.get(0).getQuantityDelta()).isEqualByComparingTo("-2");
        TenantContext.clear();

        JdbcTemplate bootstrapJdbc = new JdbcTemplate(bootstrapDataSource);
        Integer saleDeltas = bootstrapJdbc.queryForObject(
                """
                SELECT COUNT(*) FROM inventory_level_deltas
                WHERE tenant_id = ? AND variant_id = ? AND on_hand_delta < 0
                """,
                Integer.class,
                fx.owner.tenantId(),
                fx.variant.getId());
        assertThat(saleDeltas).isEqualTo(1);

        mockMvc.perform(post("/api/v1/pos/sync-receipts")
                        .header("Authorization", "Bearer " + fx.owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"%s","storeLocationId":"%s","tenderType":"CASH","taxRegion":"US",
                                  "lines":[{"variantId":"%s","quantity":2}]}]
                                """.formatted(receiptId, fx.bin.getId(), fx.variant.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.accepted").value(0));

        flushWorker.flushOnce();

        TenantContext.setTenantId(fx.owner.tenantId());
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(
                fx.owner.tenantId(), fx.variant.getId());
        BigDecimal onHand = levels.stream()
                .filter(l -> fx.bin.getId().equals(l.getLocationId()))
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(onHand).isEqualByComparingTo("8");
    }

    @Test
    void syncReceipts_rejectsUnknownStoreAndLockedModule() throws Exception {
        Fixture fx = seedCatalog();

        mockMvc.perform(post("/api/v1/pos/sync-receipts")
                        .header("Authorization", "Bearer " + fx.owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"%s","storeLocationId":"%s","tenderType":"CARD","taxRegion":"MX",
                                  "lines":[{"upc":"7700222200099","quantity":1}]}]
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected[0].reason").exists());

        tenantSubscriptionService.replaceEnabledModules(fx.owner.tenantId(), List.of(AppModule.CORE));

        mockMvc.perform(post("/api/v1/pos/sync-receipts")
                        .header("Authorization", "Bearer " + fx.owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"%s","storeLocationId":"%s","tenderType":"CASH","taxRegion":"US",
                                  "lines":[{"variantId":"%s","quantity":1}]}]
                                """.formatted(UUID.randomUUID(), fx.bin.getId(), fx.variant.getId())))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("MODULE_LOCKED"));
    }

    @Test
    void catalogSync_returnsSellableVariants() throws Exception {
        Fixture fx = seedCatalog();

        mockMvc.perform(get("/api/v1/pos/catalog-sync")
                        .header("Authorization", "Bearer " + fx.owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variantId").value(fx.variant.getId().toString()))
                .andExpect(jsonPath("$[0].upc").value("7700222200099"))
                .andExpect(jsonPath("$[0].sku").value("POS-1"))
                .andExpect(jsonPath("$[0].name").value("POS Widget"))
                .andExpect(jsonPath("$[0].retailPrice").value(4.50));
    }

    @Test
    void catalogLookup_returnsSingleVariantAnd404ForUnknownUpc() throws Exception {
        Fixture fx = seedCatalog();

        mockMvc.perform(get("/api/v1/pos/catalog/lookup")
                        .param("upc", "7700222200099")
                        .header("Authorization", "Bearer " + fx.owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value(fx.variant.getId().toString()))
                .andExpect(jsonPath("$.upc").value("7700222200099"));

        mockMvc.perform(get("/api/v1/pos/catalog/lookup")
                        .param("upc", "0000000000000")
                        .header("Authorization", "Bearer " + fx.owner.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VARIANT_NOT_FOUND"));
    }

    @Test
    void syncReceipts_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/pos/sync-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }

    private Fixture seedCatalog() {
        String slug = "pos-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "POS Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("POS");
        product.setName("POS Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("POS-1");
        variant.setBarcode("7700222200099");
        variant.setPrice(new BigDecimal("4.50"));
        variant = variantRepository.save(variant);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH-P", "/WH-P");
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH-P/Z1");
        Location bin = loc(tenantId, zone.getId(), "BIN", "B1", "/WH-P/Z1/B1");

        inventoryService.receive(variant.getId(), bin.getId(), null, new BigDecimal("10"), "POS_SEED", null);
        flushWorker.flushOnce();
        TenantContext.clear();
        return new Fixture(owner, variant, bin);
    }

    private Location loc(UUID tenantId, UUID parentId, String type, String code, String path) {
        Location loc = new Location();
        loc.setTenantId(tenantId);
        loc.setParentLocationId(parentId);
        loc.setType(type);
        loc.setCode(code);
        loc.setName(code);
        loc.setPath(path);
        return locationRepository.save(loc);
    }

    private record Fixture(TokenResponse owner, ProductVariant variant, Location bin) {
    }
}
