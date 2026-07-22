package com.invsys.service;

import com.invsys.AbstractIntegrationTest;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.common.ApiException;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedSupplierPortalServiceTest extends AbstractIntegrationTest {

    @Autowired AuthenticatedSupplierPortalService supplierPortalService;
    @Autowired AuthService authService;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listsOnlyOpenPosForMappedSupplier() {
        String slug = "asps-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "ASP Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Vendor A");
        supplier = supplierRepository.save(supplier);

        PurchaseOrder open = new PurchaseOrder();
        open.setTenantId(tenantId);
        open.setSupplierId(supplier.getId());
        open.setNumber("PO-OPEN");
        open.setStatus("SUBMITTED");
        purchaseOrderRepository.save(open);

        PurchaseOrder closed = new PurchaseOrder();
        closed.setTenantId(tenantId);
        closed.setSupplierId(supplier.getId());
        closed.setNumber("PO-CLOSED");
        closed.setStatus("CLOSED");
        purchaseOrderRepository.save(closed);

        TenantContext.setSupplierId(supplier.getId());
        var openOrders = supplierPortalService.openPurchaseOrders();
        assertThat(openOrders).extracting(PurchaseOrder::getNumber).containsExactly("PO-OPEN");
    }

    @Test
    void rejectsWhenSupplierNotMapped() {
        String slug = "aspn-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "ASP None", slug, "owner@" + slug + ".test", "password123", "Owner"));
        TenantContext.setTenantId(owner.tenantId());
        // no supplier id in context
        assertThatThrownBy(() -> supplierPortalService.openPurchaseOrders())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not mapped");
    }
}
