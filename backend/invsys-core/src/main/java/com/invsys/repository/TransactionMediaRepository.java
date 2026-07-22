package com.invsys.repository;

import com.invsys.domain.TransactionMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionMediaRepository extends JpaRepository<TransactionMedia, UUID> {
    List<TransactionMedia> findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, String entityType, UUID entityId);
}
