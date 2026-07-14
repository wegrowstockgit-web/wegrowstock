package com.invsys.repository;

import com.invsys.domain.TenantSsoConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantSsoConfigRepository extends JpaRepository<TenantSsoConfig, UUID> {
    Optional<TenantSsoConfig> findByTenantId(UUID tenantId);
}
