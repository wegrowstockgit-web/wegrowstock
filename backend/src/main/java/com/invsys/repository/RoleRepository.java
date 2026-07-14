package com.invsys.repository;

import com.invsys.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findByTenantId(UUID tenantId);
    Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);
}
