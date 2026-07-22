package com.invsys;

import com.invsys.modules.catalog.domain.Product;
import com.invsys.domain.User;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.invsys.domain.Tenant;

class RlsIsolationTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void tenantContextRestrictsVisibleRows() {
        UUID tenantA = testDataHelper.createTenant("Tenant A", "tenant-a-" + UUID.randomUUID().toString().substring(0, 8));
        UUID tenantB = testDataHelper.createTenant("Tenant B", "tenant-b-" + UUID.randomUUID().toString().substring(0, 8));

        TenantContext.setTenantId(tenantA);
        Product product = new Product();
        product.setTenantId(tenantA);
        product.setSkuRoot("SKU-A");
        product.setName("Product A");
        productRepository.save(product);
        TenantContext.clear();

        TenantContext.setTenantId(tenantB);
        assertThat(productRepository.findAll()).isEmpty();
        TenantContext.clear();

        TenantContext.setTenantId(tenantA);
        assertThat(productRepository.findAll()).hasSize(1);
    }
}
