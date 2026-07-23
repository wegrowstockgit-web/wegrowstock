package com.invsys;

import com.invsys.core.common.ApiException;
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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinCapacityValidationServiceIT extends AbstractIntegrationTest {

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
    void receiveRejectsWhenBinCapacityExceeded() {
        String slug = "bincap-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "BinCap Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-B");
        wh.setName("WH-B");
        wh.setPath("/WH-B");
        wh = locationRepository.save(wh);

        Location bin = new Location();
        bin.setTenantId(tenantId);
        bin.setParentLocationId(wh.getId());
        bin.setType("BIN");
        bin.setCode("B1");
        bin.setName("B1");
        bin.setPath("/WH-B/B1");
        bin.setMaxCubicCm(new BigDecimal("500"));
        bin.setMaxWeightKg(new BigDecimal("50"));
        bin = locationRepository.save(bin);
        final UUID binId = bin.getId();

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("BIN");
        product.setName("Bin Item");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("BIN-1");
        variant.setLength(new BigDecimal("5"));
        variant.setWidth(new BigDecimal("5"));
        variant.setHeight(new BigDecimal("4"));
        variant.setWeight(new BigDecimal("5"));
        variant = variantRepository.save(variant);
        final UUID variantId = variant.getId();

        inventoryService.receive(variantId, binId, null, new BigDecimal("4"), "SEED", null);

        assertThatThrownBy(() -> inventoryService.receive(
                variantId, binId, null, new BigDecimal("2"), "SEED", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("BIN_CAPACITY_EXCEEDED");
                });
    }
}
