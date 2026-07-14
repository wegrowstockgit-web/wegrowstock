package com.invsys.repository;

import com.invsys.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
    Optional<User> findByTenantIdAndTerminalPinHash(UUID tenantId, String terminalPinHash);
    List<User> findByTenantIdOrderByEmailAsc(UUID tenantId);
    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}
