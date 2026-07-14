package com.invsys;

import com.invsys.domain.Product;
import com.invsys.repository.ProductRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TenantPoolLeakageTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void alternatingTenantsDoNotLeakAcrossPoolConnections() throws Exception {
        UUID tenantA = testDataHelper.createTenant("Pool A", "pool-a-" + UUID.randomUUID().toString().substring(0, 8));
        UUID tenantB = testDataHelper.createTenant("Pool B", "pool-b-" + UUID.randomUUID().toString().substring(0, 8));

        TenantContext.setTenantId(tenantA);
        Product productA = new Product();
        productA.setTenantId(tenantA);
        productA.setSkuRoot("POOL-A");
        productA.setName("Pool Product A");
        productRepository.saveAndFlush(productA);
        TenantContext.clear();

        TenantContext.setTenantId(tenantB);
        Product productB = new Product();
        productB.setTenantId(tenantB);
        productB.setSkuRoot("POOL-B");
        productB.setName("Pool Product B");
        productRepository.saveAndFlush(productB);
        TenantContext.clear();

        int iterations = 120;
        CountDownLatch done = new CountDownLatch(iterations);
        AtomicInteger violations = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(12);

        for (int i = 0; i < iterations; i++) {
            UUID expectedTenant = i % 2 == 0 ? tenantA : tenantB;
            pool.submit(() -> {
                try {
                    TenantContext.setTenantId(expectedTenant);
                    productRepository.findAll().forEach(product -> {
                        if (!expectedTenant.equals(product.getTenantId())) {
                            violations.incrementAndGet();
                        }
                    });
                } finally {
                    TenantContext.clear();
                    done.countDown();
                }
            });
        }

        done.await();
        pool.shutdown();
        assertThat(violations.get()).isZero();
    }
}
