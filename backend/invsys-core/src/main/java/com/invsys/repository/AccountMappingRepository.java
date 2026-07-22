package com.invsys.repository;

import com.invsys.domain.AccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountMappingRepository extends JpaRepository<AccountMapping, UUID> {
    List<AccountMapping> findByTenantIdAndSystem(UUID tenantId, String system);

    List<AccountMapping> findByTenantIdOrderBySystemAscAccountTypeAsc(UUID tenantId);

    Optional<AccountMapping> findByTenantIdAndSystemAndAccountType(UUID tenantId, String system, String accountType);
}
