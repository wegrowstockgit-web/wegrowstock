package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.inventory.domain.LicensePlate;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.inventory.repository.LicensePlateRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LpnMoveHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired LicensePlateRepository licensePlateRepository;
    @Autowired InventoryLevelRepository levelRepository;
    @Autowired InventoryService inventoryService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void moveLpnTransfersAllLevelsAndWritesLedger() throws Exception {
        String slug = "lpn-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "LPN Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("LPN");
        product.setName("LPN Product");
        product = productRepository.save(product);

        ProductVariant v1 = variant(tenantId, product.getId(), "LPN-1", "8800111100011");
        ProductVariant v2 = variant(tenantId, product.getId(), "LPN-2", "8800111100028");

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH-LPN", "/WH-LPN");
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH-LPN/Z1");
        Location aisle = loc(tenantId, zone.getId(), "AISLE", "A1", "/WH-LPN/Z1/A1");
        Location from = loc(tenantId, aisle.getId(), "BIN", "B1", "/WH-LPN/Z1/A1/B1");
        Location to = loc(tenantId, aisle.getId(), "BIN", "B2", "/WH-LPN/Z1/A1/B2");

        LicensePlate lpn = inventoryService.createLicensePlate("LPN-PALLET-1", from.getId());
        inventoryService.receiveOntoLpn(v1.getId(), lpn.getId(), new BigDecimal("5"));
        inventoryService.receiveOntoLpn(v2.getId(), lpn.getId(), new BigDecimal("3"));
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/inventory/lpns/move")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lpnBarcode":"LPN-PALLET-1","destinationBarcode":"B2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lpnBarcode").value("LPN-PALLET-1"))
                .andExpect(jsonPath("$.destinationLocationId").value(to.getId().toString()))
                .andExpect(jsonPath("$.linesMoved").value(2));

        TenantContext.setTenantId(tenantId);
        LicensePlate moved = licensePlateRepository.findById(lpn.getId()).orElseThrow();
        assertThat(moved.getLocationId()).isEqualTo(to.getId());
        assertThat(moved.getStatus()).isEqualTo("OPEN");

        List<InventoryLevel> atDest = levelRepository.findByTenantIdAndLpnId(tenantId, lpn.getId()).stream()
                .filter(l -> to.getId().equals(l.getLocationId()) && l.getOnHand().signum() > 0)
                .toList();
        assertThat(atDest).hasSize(2);
        assertThat(atDest.stream().map(InventoryLevel::getOnHand).map(BigDecimal::intValueExact))
                .containsExactlyInAnyOrder(5, 3);
    }

    private ProductVariant variant(UUID tenantId, UUID productId, String sku, String barcode) {
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(productId);
        variant.setSku(sku);
        variant.setBarcode(barcode);
        return variantRepository.save(variant);
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
