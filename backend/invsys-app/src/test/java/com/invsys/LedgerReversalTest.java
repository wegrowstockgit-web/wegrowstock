package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.common.ApiException;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.service.InventoryLevelFlushWorker;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LedgerReversalTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLevelRepository levelRepository;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryLevelFlushWorker flushWorker;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void reverseLedgerEntryPostsCompensatingAdjustAndRestoresLevels() {
        Fixture fx = seed("rev-svc");
        TenantContext.setTenantId(fx.tenantId());

        InventoryLedger original = inventoryService.receive(
                fx.variantId(), fx.locationId(), null, new BigDecimal("10"), null, null);
        assertOnHand(fx, "10");

        InventoryLedger reversal = inventoryService.reverseLedgerEntry(original.getId());

        assertThat(reversal.getMovementType()).isEqualTo("ADJUST");
        assertThat(reversal.getReasonCode()).isEqualTo("ERROR_CORRECTION");
        assertThat(reversal.getQuantityDelta()).isEqualByComparingTo("-10");
        assertThat(reversal.getReversalOfLedgerId()).isEqualTo(original.getId());
        assertThat(reversal.getVariantId()).isEqualTo(original.getVariantId());
        assertThat(reversal.getLocationId()).isEqualTo(original.getLocationId());
        assertOnHand(fx, "0");

        assertThatThrownBy(() -> inventoryService.reverseLedgerEntry(original.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("ALREADY_REVERSED");
                });

        assertThatThrownBy(() -> inventoryService.reverseLedgerEntry(reversal.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo("CANNOT_REVERSE_REVERSAL");
                });
    }

    @Test
    void reverseEndpointAndLedgerListAreTenantScoped() throws Exception {
        Fixture fx = seed("rev-http");
        TenantContext.setTenantId(fx.tenantId());
        InventoryLedger original = inventoryService.receive(
                fx.variantId(), fx.locationId(), null, new BigDecimal("5"), null, null);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/inventory/ledger")
                        .header("Authorization", "Bearer " + fx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(original.getId().toString()));

        mockMvc.perform(post("/api/v1/inventory/ledger/" + original.getId() + "/reverse")
                        .header("Authorization", "Bearer " + fx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ERROR_CORRECTION"))
                .andExpect(jsonPath("$.quantityDelta").value(-5))
                .andExpect(jsonPath("$.reversalOfLedgerId").value(original.getId().toString()));

        Fixture other = seed("rev-other");
        mockMvc.perform(post("/api/v1/inventory/ledger/" + original.getId() + "/reverse")
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ledgerListFiltersByVariantId() throws Exception {
        Fixture fx = seed("rev-filter");
        TenantContext.setTenantId(fx.tenantId());

        Product product2 = new Product();
        product2.setTenantId(fx.tenantId());
        product2.setSkuRoot("REV2");
        product2.setName("Other SKU");
        product2 = productRepository.save(product2);

        ProductVariant variant2 = new ProductVariant();
        variant2.setTenantId(fx.tenantId());
        variant2.setProductId(product2.getId());
        variant2.setSku("REV2-1");
        variant2 = variantRepository.save(variant2);

        InventoryLedger keep = inventoryService.receive(
                fx.variantId(), fx.locationId(), null, new BigDecimal("3"), null, null);
        inventoryService.receive(
                variant2.getId(), fx.locationId(), null, new BigDecimal("7"), null, null);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/inventory/ledger")
                        .param("variantId", fx.variantId().toString())
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + fx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(keep.getId().toString()))
                .andExpect(jsonPath("$[0].variantId").value(fx.variantId().toString()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    private void assertOnHand(Fixture fx, String expected) {
        flushWorker.flushOnce();
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(fx.tenantId(), fx.variantId());
        BigDecimal onHand = levels.stream()
                .filter(l -> l.getLocationId().equals(fx.locationId()))
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(onHand).isEqualByComparingTo(expected);
    }

    private Fixture seed(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Reverse Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("REV");
        product.setName("Reverse Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("REV-" + slug.substring(0, 6));
        variant = variantRepository.save(variant);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-REV");
        location.setName("Reverse WH");
        location.setPath("/WH-REV");
        location = locationRepository.save(location);

        TenantContext.clear();
        return new Fixture(tenantId, owner.accessToken(), variant.getId(), location.getId());
    }

    private record Fixture(UUID tenantId, String token, UUID variantId, UUID locationId) {
    }
}
