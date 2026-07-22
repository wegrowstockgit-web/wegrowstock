package com.invsys.service;

import com.invsys.AbstractIntegrationTest;
import com.invsys.TestDataHelper;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.invsys.modules.inventory.service.InventoryService;

class PutawayStrategyServiceTest extends AbstractIntegrationTest {

    @Autowired PutawayStrategyService putawayStrategyService;
    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void consolidationBeatsEmptyBin() {
        UUID tenantId = testDataHelper.createTenant("Strat Consol", "psc-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        ProductVariant variant = variant(tenantId, "PSC-1", "C", "AMBIENT", false);
        Location wh = loc(tenantId, null, "WAREHOUSE", "WH", "/WH", "STANDARD", "AMBIENT", false, 0, 0);
        Location aisle = loc(tenantId, wh.getId(), "AISLE", "A1", "/WH/A1", "STANDARD", "AMBIENT", false, 10, 10);
        Location stocked = loc(tenantId, aisle.getId(), "BIN", "B1", "/WH/A1/B1", "STANDARD", "AMBIENT", false, 10, 10);
        loc(tenantId, aisle.getId(), "BIN", "B2", "/WH/A1/B2", "STANDARD", "AMBIENT", false, 20, 20);

        inventoryService.receive(variant.getId(), stocked.getId(), null, BigDecimal.TEN, "SEED", null);
        TenantContext.setWarehouseId(wh.getId());

        var directive = putawayStrategyService.suggest(variant.getId());
        assertThat(directive.strategy()).isEqualTo("CONSOLIDATION");
        assertThat(directive.locationId()).isEqualTo(stocked.getId());
        assertThat(directive.binLabel()).isEqualTo("B1");
    }

    @Test
    void coldSkuDirectedToRefrigeratedBin() {
        UUID tenantId = testDataHelper.createTenant("Strat Cold", "pcd-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        ProductVariant variant = variant(tenantId, "PCD-1", "C", "REFRIGERATED", false);
        Location wh = loc(tenantId, null, "WAREHOUSE", "WH", "/WH", "STANDARD", "AMBIENT", false, 0, 0);
        Location ambient = loc(tenantId, wh.getId(), "BIN", "AMB", "/WH/AMB", "STANDARD", "AMBIENT", false, 5, 5);
        Location cooler = loc(tenantId, wh.getId(), "BIN", "COOL", "/WH/COOL", "STANDARD", "REFRIGERATED", false, 8, 8);
        TenantContext.setWarehouseId(wh.getId());

        var directive = putawayStrategyService.suggest(variant.getId());
        assertThat(directive.strategy()).isEqualTo("COLD_ZONE");
        assertThat(directive.locationId()).isEqualTo(cooler.getId());
        assertThat(ambient.getId()).isNotEqualTo(directive.locationId());
    }

    @Test
    void hazmatSkuDirectedToHazmatBin() {
        UUID tenantId = testDataHelper.createTenant("Strat Haz", "phz-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        ProductVariant variant = variant(tenantId, "PHZ-1", "C", "AMBIENT", true);
        Location wh = loc(tenantId, null, "WAREHOUSE", "WH", "/WH", "STANDARD", "AMBIENT", false, 0, 0);
        loc(tenantId, wh.getId(), "BIN", "SAFE", "/WH/SAFE", "STANDARD", "AMBIENT", false, 5, 5);
        Location hazBin = loc(tenantId, wh.getId(), "BIN", "HAZ", "/WH/HAZ", "STANDARD", "AMBIENT", true, 6, 6);
        TenantContext.setWarehouseId(wh.getId());

        var directive = putawayStrategyService.suggest(variant.getId());
        assertThat(directive.strategy()).isEqualTo("HAZMAT_ZONE");
        assertThat(directive.locationId()).isEqualTo(hazBin.getId());
    }

    @Test
    void aVelocityPrefersNearDockEmptyBin() {
        UUID tenantId = testDataHelper.createTenant("Strat Vel", "pvl-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        ProductVariant variant = variant(tenantId, "PVL-1", "A", "AMBIENT", false);
        Location wh = loc(tenantId, null, "WAREHOUSE", "WH", "/WH", "STANDARD", "AMBIENT", false, 0, 0);
        Location dock = loc(tenantId, wh.getId(), "ZONE", "DOCK", "/WH/DOCK", "RECEIVING", "AMBIENT", false, 0, 0);
        Location far = loc(tenantId, wh.getId(), "BIN", "FAR", "/WH/FAR", "RESERVE", "AMBIENT", false, 100, 100);
        Location near = loc(tenantId, dock.getId(), "BIN", "NEAR", "/WH/DOCK/NEAR", "PICK_FACE", "AMBIENT", false, 1, 1);
        TenantContext.setWarehouseId(wh.getId());

        var directive = putawayStrategyService.suggest(variant.getId());
        assertThat(directive.strategy()).isEqualTo("VELOCITY_DOCK");
        assertThat(directive.locationId()).isEqualTo(near.getId());
        assertThat(far.getId()).isNotEqualTo(directive.locationId());
    }

    private ProductVariant variant(UUID tenantId, String sku, String abc, String temp, boolean hazmat) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot(sku);
        product.setName(sku);
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku(sku);
        variant.setBarcode("BC-" + sku);
        variant.setAbcClassification(abc);
        variant.setStorageTempZone(temp);
        variant.setHazmat(hazmat);
        return variantRepository.save(variant);
    }

    private Location loc(UUID tenantId, UUID parentId, String type, String code, String path,
                         String zoneBehavior, String temp, boolean hazmat, double x, double y) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setParentLocationId(parentId);
        location.setType(type);
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        location.setZoneBehavior(zoneBehavior);
        location.setStorageTempZone(temp);
        location.setAllowsHazmat(hazmat);
        location.setCoordX(BigDecimal.valueOf(x));
        location.setCoordY(BigDecimal.valueOf(y));
        return locationRepository.save(location);
    }
}
