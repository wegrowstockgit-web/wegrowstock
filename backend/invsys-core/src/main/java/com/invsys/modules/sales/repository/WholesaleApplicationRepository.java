package com.invsys.modules.sales.repository;

import com.invsys.modules.sales.domain.WholesaleApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WholesaleApplicationRepository extends JpaRepository<WholesaleApplication, UUID> {

    List<WholesaleApplication> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<WholesaleApplication> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<WholesaleApplication> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndEmailIgnoreCaseAndStatus(UUID tenantId, String email, String status);
}
