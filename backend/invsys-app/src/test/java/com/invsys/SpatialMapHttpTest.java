package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SpatialMapHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired InventoryService inventoryService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void patchCoordinatesAndHeatmapReflectLedgerActivity() throws Exception {
        String slug = "map-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Map Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH-M", "/WH-M");
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH-M/Z1");
        Location aisle = loc(tenantId, zone.getId(), "AISLE", "A1", "/WH-M/Z1/A1");
        Location bin = loc(tenantId, aisle.getId(), "BIN", "B1", "/WH-M/Z1/A1/B1");

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("MAP");
        product.setName("Map Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("MAP-1");
        variant.setBarcode("6600111100014");
        variant = variantRepository.save(variant);

        inventoryService.receive(variant.getId(), bin.getId(), null, new BigDecimal("4"), "SEED", null);
        TenantContext.clear();

        mockMvc.perform(patch("/api/v1/locations/" + bin.getId() + "/coordinates")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coordX\":42.5,\"coordY\":17.25,\"coordZ\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordX").value(42.5))
                .andExpect(jsonPath("$.coordY").value(17.25));

        TenantContext.setTenantId(tenantId);
        Location updated = locationRepository.findById(bin.getId()).orElseThrow();
        assertThat(updated.getCoordX()).isEqualByComparingTo("42.5");
        assertThat(updated.getCoordY()).isEqualByComparingTo("17.25");
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/locations/heatmap")
                        .param("days", "7")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.locationId=='" + bin.getId() + "')].movementCount").exists());

        mockMvc.perform(post("/api/v1/locations/walkable-edges")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nodeAId":"%s","nodeBId":"%s","distance":12.5}
                                """.formatted(bin.getId(), aisle.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distance").value(12.5));

        mockMvc.perform(get("/api/v1/locations/walkable-edges")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nodeAId").exists());
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
