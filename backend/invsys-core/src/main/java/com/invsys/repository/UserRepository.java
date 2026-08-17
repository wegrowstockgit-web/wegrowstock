package com.invsys.repository;

import com.invsys.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<User> findByTenantIdAndAvatarUrl(UUID tenantId, String avatarUrl);
    Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
    Optional<User> findByTenantIdAndTerminalPinHash(UUID tenantId, String terminalPinHash);
    List<User> findByTenantIdOrderByEmailAsc(UUID tenantId);
    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN UserRole ur ON ur.userId = u.id AND ur.tenantId = u.tenantId
            JOIN Role r ON r.id = ur.roleId AND r.tenantId = u.tenantId
            WHERE u.tenantId = :tenantId
              AND u.status = 'ACTIVE'
              AND u.terminalPinHash IS NOT NULL
              AND r.code IN :roleCodes
            """)
    List<User> findActiveUsersWithPinAndRoles(
            @Param("tenantId") UUID tenantId,
            @Param("roleCodes") Collection<String> roleCodes);

    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN UserRole ur ON ur.userId = u.id AND ur.tenantId = u.tenantId
            JOIN Role r ON r.id = ur.roleId AND r.tenantId = u.tenantId
            LEFT JOIN RolePermission rp ON rp.roleId = r.id AND rp.tenantId = u.tenantId
              AND rp.permissionKey = :permissionKey AND rp.granted = true
            WHERE u.tenantId = :tenantId
              AND u.status = 'ACTIVE'
              AND u.terminalPinHash IS NOT NULL
              AND (rp.id IS NOT NULL OR r.code IN ('OWNER', 'ADMIN'))
            """)
    List<User> findActiveUsersWithPinAndPermission(
            @Param("tenantId") UUID tenantId,
            @Param("permissionKey") String permissionKey);
}
