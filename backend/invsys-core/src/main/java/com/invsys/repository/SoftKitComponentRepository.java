package com.invsys.repository;

import com.invsys.domain.SoftKitComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SoftKitComponentRepository extends JpaRepository<SoftKitComponent, UUID> {
    List<SoftKitComponent> findByTenantIdAndParentKitIdOrderByCreatedAtAsc(UUID tenantId, UUID parentKitId);
}
