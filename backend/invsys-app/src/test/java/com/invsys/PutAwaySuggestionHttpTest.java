package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PutAwaySuggestionHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void suggestsConsolidationThenEmptyBin() throws Exception {
        String slug = "pa-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "PutAway Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PA");
        product.setName("PutAway");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PA-1");
        variant.setBarcode("9900111122223");
        variant = variantRepository.save(variant);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH-PA", "/WH-PA");
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH-PA/Z1");
        Location aisle = loc(tenantId, zone.getId(), "AISLE", "A1", "/WH-PA/Z1/A1");
        Location stocked = loc(tenantId, aisle.getId(), "BIN", "B1", "/WH-PA/Z1/A1/B1");
        Location empty = loc(tenantId, aisle.getId(), "BIN", "B2", "/WH-PA/Z1/A1/B2");

        inventoryService.receive(variant.getId(), stocked.getId(), null, new BigDecimal("3"), "SEED", null);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/locations/putaway-suggestions")
                        .param("variantId", variant.getId().toString())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(stocked.getId().toString()))
                .andExpect(jsonPath("$.strategy").value("CONSOLIDATION"));

        // Different variant with no stock → empty BIN under warehouse
        TenantContext.setTenantId(tenantId);
        ProductVariant other = new ProductVariant();
        other.setTenantId(tenantId);
        other.setProductId(product.getId());
        other.setSku("PA-2");
        other = variantRepository.save(other);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/locations/putaway-suggestions")
                        .param("variantId", other.getId().toString())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(empty.getId().toString()))
                .andExpect(jsonPath("$.strategy").value("EMPTY_BIN"));
    }

    private Location loc(UUID tenantId, UUID parentId, String type, String code, String path) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setParentLocationId(parentId);
        location.setType(type);
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        return locationRepository.save(location);
    }
}
