package com.invsys.modules.sales.repository;

import com.invsys.modules.sales.domain.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<Invoice> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);
    long countByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);
    Optional<Invoice> findByTenantIdAndNumber(UUID tenantId, String number);
    List<Invoice> findByTenantIdAndSalesOrderId(UUID tenantId, UUID salesOrderId);
    List<Invoice> findByTenantIdAndSalesOrderIdIn(UUID tenantId, Collection<UUID> salesOrderIds);
    Optional<Invoice> findByTenantIdAndShipmentId(UUID tenantId, UUID shipmentId);
    List<Invoice> findByTenantIdAndStatusAndDueAtBefore(UUID tenantId, String status, Instant dueAtBefore);

    @Query("""
            SELECT inv FROM Invoice inv
            WHERE inv.tenantId = :tenantId
              AND (:status = '' OR inv.status = :status)
              AND (:keyword = ''
                OR LOWER(inv.number) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(inv.status) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(inv.currency) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR EXISTS (
                    SELECT 1 FROM Customer c
                    WHERE c.id = inv.customerId AND c.tenantId = inv.tenantId
                      AND LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                ))
            """)
    Page<Invoice> search(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable);
}
