package com.invsys.repository;

import com.invsys.domain.CapitalCreditLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CapitalCreditLineRepository extends JpaRepository<CapitalCreditLine, UUID> {
    Optional<CapitalCreditLine> findByTenantId(UUID tenantId);
}
