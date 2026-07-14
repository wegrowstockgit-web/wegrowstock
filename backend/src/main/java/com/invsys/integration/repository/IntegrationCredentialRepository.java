package com.invsys.integration.repository;

import com.invsys.integration.domain.IntegrationCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IntegrationCredentialRepository extends JpaRepository<IntegrationCredential, UUID> {
    Optional<IntegrationCredential> findByTenantIdAndSystem(UUID tenantId, String system);
}
