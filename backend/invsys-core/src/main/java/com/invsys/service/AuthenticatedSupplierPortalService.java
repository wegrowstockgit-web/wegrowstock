package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuthenticatedSupplierPortalService {

    private static final List<String> OPEN_STATUSES = List.of(
            "SUBMITTED", "IN_TRANSIT", "PARTIALLY_RECEIVED");

    private final PurchaseOrderRepository purchaseOrderRepository;

    public AuthenticatedSupplierPortalService(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> openPurchaseOrders() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID supplierId = TenantContext.getSupplierId()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "SUPPLIER_NOT_MAPPED",
                        "Authenticated user is not mapped to a supplier"));
        return purchaseOrderRepository.findByTenantIdAndSupplierIdAndStatusInOrderByExpectedAtAsc(
                tenantId, supplierId, OPEN_STATUSES);
    }
}
