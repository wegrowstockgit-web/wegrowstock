package com.invsys.modules.purchasing.repository;

import com.invsys.modules.purchasing.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    List<PurchaseOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    java.util.Optional<PurchaseOrder> findByTenantIdAndId(UUID tenantId, UUID id);

    java.util.Optional<PurchaseOrder> findByTenantIdAndNumberIgnoreCase(UUID tenantId, String number);

    List<PurchaseOrder> findByTenantIdAndSupplierIdAndStatusInOrderByExpectedAtAsc(
            UUID tenantId, UUID supplierId, List<String> statuses);
}
