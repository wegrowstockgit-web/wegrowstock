package com.invsys.repository;

import com.invsys.domain.TenantDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantDomainRepository extends JpaRepository<TenantDomain, UUID> {
    List<TenantDomain> findByTenantIdOrderByDomainNameAsc(UUID tenantId);

    Optional<TenantDomain> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<TenantDomain> findByDomainNameIgnoreCaseAndVerificationStatus(String domainName, String verificationStatus);
}
