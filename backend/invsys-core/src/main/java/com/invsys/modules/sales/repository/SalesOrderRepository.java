package com.invsys.modules.sales.repository;

import com.invsys.modules.sales.domain.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {
    List<SalesOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<SalesOrder> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);
    java.util.Optional<SalesOrder> findByTenantIdAndNumberIgnoreCase(UUID tenantId, String number);
    long countByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);
}
