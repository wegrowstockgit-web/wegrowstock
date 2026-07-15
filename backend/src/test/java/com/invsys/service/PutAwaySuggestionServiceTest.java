package com.invsys.service;

import com.invsys.AbstractIntegrationTest;
import com.invsys.TestDataHelper;
import com.invsys.common.ApiException;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PutAwaySuggestionServiceTest extends AbstractIntegrationTest {

    @Autowired PutAwaySuggestionService putAwaySuggestionService;
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
    void prefersConsolidationThenFallsBackToEmptyBin() {
        UUID tenantId = testDataHelper.createTenant("PutAway Svc", "pas-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PAS");
        product.setName("PutAway");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PAS-1");
        variant = variantRepository.save(variant);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH", "/WH");
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z", "/WH/Z");
        Location aisle = loc(tenantId, zone.getId(), "AISLE", "A", "/WH/Z/A");
        Location stocked = loc(tenantId, aisle.getId(), "BIN", "B1", "/WH/Z/A/B1");
        Location empty = loc(tenantId, aisle.getId(), "BIN", "B2", "/WH/Z/A/B2");

        inventoryService.receive(variant.getId(), stocked.getId(), null,
                java.math.BigDecimal.TEN, "SEED", null);

        TenantContext.setWarehouseId(wh.getId());
        var consolidation = putAwaySuggestionService.suggest(variant.getId());
        assertThat(consolidation.strategy()).isEqualTo("CONSOLIDATION");
        assertThat(consolidation.locationId()).isEqualTo(stocked.getId());

        ProductVariant other = new ProductVariant();
        other.setTenantId(tenantId);
        other.setProductId(product.getId());
        other.setSku("PAS-2");
        other = variantRepository.save(other);

        var emptyBin = putAwaySuggestionService.suggest(other.getId());
        assertThat(emptyBin.strategy()).isEqualTo("EMPTY_BIN");
        assertThat(emptyBin.locationId()).isEqualTo(empty.getId());
    }

    @Test
    void throwsWhenNoBinsAvailable() {
        UUID tenantId = testDataHelper.createTenant("PutAway Empty", "pae-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PAE");
        product.setName("No Bin");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PAE-1");
        final ProductVariant saved = variantRepository.save(variant);

        assertThatThrownBy(() -> putAwaySuggestionService.suggest(saved.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No consolidation or empty BIN");
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
