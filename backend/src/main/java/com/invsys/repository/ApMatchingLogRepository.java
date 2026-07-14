package com.invsys.repository;

import com.invsys.domain.ApMatchingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApMatchingLogRepository extends JpaRepository<ApMatchingLog, UUID> {
    List<ApMatchingLog> findByTenantIdAndPoIdOrderByCreatedAtDesc(UUID tenantId, UUID poId);
}
