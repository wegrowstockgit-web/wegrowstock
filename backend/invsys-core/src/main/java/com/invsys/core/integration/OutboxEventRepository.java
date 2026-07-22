package com.invsys.core.integration;

import com.invsys.core.integration.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>, OutboxEventRepositoryCustom {
    List<OutboxEvent> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<OutboxEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
