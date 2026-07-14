package com.invsys.repository;

import com.invsys.domain.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {
    List<SalesOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<SalesOrder> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);
    long countByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);
}
