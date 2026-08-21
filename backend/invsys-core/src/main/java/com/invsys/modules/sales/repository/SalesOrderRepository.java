package com.invsys.modules.sales.repository;

import com.invsys.modules.sales.domain.SalesOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {
    List<SalesOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<SalesOrder> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);
    java.util.Optional<SalesOrder> findByTenantIdAndNumberIgnoreCase(UUID tenantId, String number);
    long countByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);

    @Query("""
            SELECT so FROM SalesOrder so
            WHERE so.tenantId = :tenantId
              AND (:status = '' OR so.status = :status)
              AND (:keyword = ''
                OR LOWER(so.number) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(so.status) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(so.channel) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(so.customerPoNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR EXISTS (
                    SELECT 1 FROM Customer c
                    WHERE c.id = so.customerId AND c.tenantId = so.tenantId
                      AND LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                ))
            """)
    Page<SalesOrder> search(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable);
}
