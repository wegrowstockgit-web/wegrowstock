package com.invsys.repository;

import com.invsys.domain.EdiDocumentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EdiDocumentLogRepository extends JpaRepository<EdiDocumentLog, UUID> {
    List<EdiDocumentLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
