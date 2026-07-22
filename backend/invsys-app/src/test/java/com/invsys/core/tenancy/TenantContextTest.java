package com.invsys.core.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void supplierAndCustomerContextRoundTrip() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID supplier = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();

        TenantContext.setTenantId(tenant);
        TenantContext.setUserId(user);
        TenantContext.setSupplierId(supplier);
        TenantContext.setCustomerId(customer);
        TenantContext.setWarehouseId(warehouse);
        TenantContext.setAuthorizedWarehouseIds(List.of(warehouse));
        TenantContext.setBootstrap(true);

        assertThat(TenantContext.requireTenantId()).isEqualTo(tenant);
        assertThat(TenantContext.getUserId()).contains(user);
        assertThat(TenantContext.requireSupplierId()).isEqualTo(supplier);
        assertThat(TenantContext.requireCustomerId()).isEqualTo(customer);
        assertThat(TenantContext.getWarehouseId()).contains(warehouse);
        assertThat(TenantContext.getAuthorizedWarehouseIds()).containsExactly(warehouse);
        assertThat(TenantContext.isBootstrap()).isTrue();

        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isEmpty();
        assertThat(TenantContext.getSupplierId()).isEmpty();
        assertThatThrownBy(TenantContext::requireSupplierId)
                .isInstanceOf(IllegalStateException.class);
    }
}
