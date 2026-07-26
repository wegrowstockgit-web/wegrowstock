package com.invsys.repository;

import com.invsys.domain.RtvOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RtvOrderRepository extends JpaRepository<RtvOrder, UUID> {

    List<RtvOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<RtvOrder> findByTenantIdAndId(UUID tenantId, UUID id);
}
