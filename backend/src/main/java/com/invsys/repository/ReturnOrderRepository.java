package com.invsys.repository;

import com.invsys.domain.ReturnOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReturnOrderRepository extends JpaRepository<ReturnOrder, UUID> {
    List<ReturnOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ReturnOrder> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    Optional<ReturnOrder> findByTenantIdAndNumber(UUID tenantId, String number);
}
