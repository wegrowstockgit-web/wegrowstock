package com.invsys.repository;

import com.invsys.domain.PalletManifest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PalletManifestRepository extends JpaRepository<PalletManifest, UUID> {

    List<PalletManifest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<PalletManifest> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<PalletManifest> findFirstByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    long countByTenantId(UUID tenantId);
}
