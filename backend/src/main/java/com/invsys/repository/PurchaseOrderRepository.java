package com.invsys.repository;

import com.invsys.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    List<PurchaseOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    java.util.Optional<PurchaseOrder> findByTenantIdAndId(UUID tenantId, UUID id);

    List<PurchaseOrder> findByTenantIdAndSupplierIdAndStatusInOrderByExpectedAtAsc(
            UUID tenantId, UUID supplierId, List<String> statuses);
}
