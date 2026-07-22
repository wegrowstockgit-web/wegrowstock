package com.invsys.repository;

import com.invsys.domain.WalkableEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalkableEdgeRepository extends JpaRepository<WalkableEdge, UUID> {
    List<WalkableEdge> findByTenantId(UUID tenantId);

    long countByTenantId(UUID tenantId);
}
