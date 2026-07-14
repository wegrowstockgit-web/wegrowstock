package com.invsys.service;

import com.invsys.domain.Bom;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.BomRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class KitServiceTest extends com.invsys.AbstractIntegrationTest {

    @Autowired KitService kitService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired BomRepository bomRepository;
    @Autowired ManufacturingService manufacturingService;
    @Autowired com.invsys.TestDataHelper testDataHelper;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void isKitReturnsTrueForVariantFlag() {
        UUID tenantId = testDataHelper.createTenant("Kit Flag", "kitf-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("KF");
        product.setName("Kit Flag");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("KF-1");
        variant.setKit(true);
        variant = variantRepository.save(variant);

        assertThat(kitService.isKit(variant.getId())).isTrue();
    }

    @Test
    void isKitReturnsTrueForAutoAssembleBomWithoutVariantFlag() {
        UUID tenantId = testDataHelper.createTenant("Auto BOM", "autb-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product parentProduct = new Product();
        parentProduct.setTenantId(tenantId);
        parentProduct.setSkuRoot("AB");
        parentProduct.setName("Auto Parent");
        parentProduct = productRepository.save(parentProduct);

        Product compProduct = new Product();
        compProduct.setTenantId(tenantId);
        compProduct.setSkuRoot("AC");
        compProduct.setName("Auto Comp");
        compProduct = productRepository.save(compProduct);

        ProductVariant parent = new ProductVariant();
        parent.setTenantId(tenantId);
        parent.setProductId(parentProduct.getId());
        parent.setSku("AB-1");
        parent = variantRepository.save(parent);

        ProductVariant component = new ProductVariant();
        component.setTenantId(tenantId);
        component.setProductId(compProduct.getId());
        component.setSku("AC-1");
        component = variantRepository.save(component);

        Bom bom = manufacturingService.createBom(parent.getId(), "Auto",
                List.of(new ManufacturingService.BomLineInput(component.getId(), BigDecimal.ONE)), true);

        assertThat(bom.isAutoAssemble()).isTrue();
        assertThat(kitService.isKit(parent.getId())).isTrue();
    }

    @Test
    void explodeComponentsReturnsBomQuantities() {
        UUID tenantId = testDataHelper.createTenant("Explode", "exp-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product parentProduct = new Product();
        parentProduct.setTenantId(tenantId);
        parentProduct.setSkuRoot("EX");
        parentProduct.setName("Explode Parent");
        parentProduct = productRepository.save(parentProduct);

        Product compProduct = new Product();
        compProduct.setTenantId(tenantId);
        compProduct.setSkuRoot("EC");
        compProduct.setName("Explode Comp");
        compProduct = productRepository.save(compProduct);

        ProductVariant parent = new ProductVariant();
        parent.setTenantId(tenantId);
        parent.setProductId(parentProduct.getId());
        parent.setSku("EX-1");
        parent = variantRepository.save(parent);

        ProductVariant component = new ProductVariant();
        component.setTenantId(tenantId);
        component.setProductId(compProduct.getId());
        component.setSku("EC-1");
        component = variantRepository.save(component);

        manufacturingService.createBom(parent.getId(), "Explode BOM",
                List.of(new ManufacturingService.BomLineInput(component.getId(), new BigDecimal("4"))));

        List<KitService.BomComponent> exploded = kitService.explodeComponents(parent.getId());
        assertThat(exploded).hasSize(1);
        assertThat(exploded.getFirst().quantityPerParent()).isEqualByComparingTo(new BigDecimal("4"));
    }
}
