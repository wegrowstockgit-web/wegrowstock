package com.invsys.repository;

import com.invsys.domain.ProductionOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, UUID> {
    List<ProductionOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
