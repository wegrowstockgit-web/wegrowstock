package com.invsys.modules.purchasing.repository;

import com.invsys.modules.purchasing.domain.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    List<PurchaseOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    java.util.Optional<PurchaseOrder> findByTenantIdAndId(UUID tenantId, UUID id);

    java.util.Optional<PurchaseOrder> findByTenantIdAndNumberIgnoreCase(UUID tenantId, String number);

    List<PurchaseOrder> findByTenantIdAndSupplierIdAndStatusInOrderByExpectedAtAsc(
            UUID tenantId, UUID supplierId, List<String> statuses);

    @Query("""
            SELECT po FROM PurchaseOrder po
            WHERE po.tenantId = :tenantId
              AND (:keyword = ''
                OR LOWER(po.number) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(po.status) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(po.notes, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR EXISTS (
                    SELECT 1 FROM Supplier s
                    WHERE s.id = po.supplierId AND s.tenantId = po.tenantId
                      AND LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                ))
            """)
    Page<PurchaseOrder> search(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
