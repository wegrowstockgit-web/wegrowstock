package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.invsys.modules.catalog.domain.Lot;

@AutoConfigureMockMvc
class GracefulLotHandlingTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired TenantSettingsRepository tenantSettingsRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void receiveSinksVendorLotIntoLedgerMetadataWhenNotLotTracked() throws Exception {
        String slug = "glot-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Grace Lot", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantSettingsRepository.save(TenantSettings.withDefaults(tenantId)));
        Map<String, Object> updated = new HashMap<>(settings.getSettings());
        updated.put("allow_blind_receiving", true);
        settings.setSettings(updated);
        tenantSettingsRepository.save(settings);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("GLOT");
        product.setName("Grace Lot Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("GLOT-1");
        variant.setBarcode("01234567890128");
        variant.setLotTracked(false);
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-GLOT");
        wh.setName("Grace WH");
        wh.setPath("/WH-GLOT");
        wh = locationRepository.save(wh);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barcode":"01234567890128",
                                  "warehouseId":"%s",
                                  "mode":"receive",
                                  "gtin":"01234567890128",
                                  "lotNumber":"LOT123",
                                  "quantity":2,
                                  "isGs1":true,
                                  "metadata":{"vendor_lot_captured":"LOT123"}
                                }
                                """.formatted(wh.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLotTracked").value(false))
                .andExpect(jsonPath("$.lotLoggedNotTracked").value(true));

        TenantContext.setTenantId(tenantId);
        InventoryLedger recv = ledgerRepository
                .findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, variant.getId())
                .stream()
                .filter(e -> "RECEIVE".equals(e.getMovementType()))
                .findFirst()
                .orElseThrow();
        assertThat(recv.getLotId()).isNull();
        assertThat(recv.getMetadata()).containsEntry("vendor_lot_captured", "LOT123");
    }

    @Test
    void receiveBindsLotWhenLotTracked() throws Exception {
        String slug = "tlot-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Track Lot", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> tenantSettingsRepository.save(TenantSettings.withDefaults(tenantId)));
        Map<String, Object> updated = new HashMap<>(settings.getSettings());
        updated.put("allow_blind_receiving", true);
        settings.setSettings(updated);
        tenantSettingsRepository.save(settings);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("TLOT");
        product.setName("Track Lot Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("TLOT-1");
        variant.setBarcode("01990000111112");
        variant.setLotTracked(true);
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-TLOT");
        wh.setName("Track WH");
        wh.setPath("/WH-TLOT");
        wh = locationRepository.save(wh);
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/fulfillment/scan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "barcode":"01990000111112",
                                  "warehouseId":"%s",
                                  "mode":"receive",
                                  "lotNumber":"BOUND-1",
                                  "quantity":1,
                                  "isGs1":true
                                }
                                """.formatted(wh.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLotTracked").value(true))
                .andExpect(jsonPath("$.lotLoggedNotTracked").value(false));

        TenantContext.setTenantId(tenantId);
        InventoryLedger recv = ledgerRepository
                .findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, variant.getId())
                .stream()
                .filter(e -> "RECEIVE".equals(e.getMovementType()))
                .findFirst()
                .orElseThrow();
        assertThat(recv.getLotId()).isNotNull();
        assertThat(recv.getMetadata()).doesNotContainKey("vendor_lot_captured");
    }
}
