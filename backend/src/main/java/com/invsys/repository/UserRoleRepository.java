package com.invsys.repository;

import com.invsys.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
    List<UserRole> findByUserId(UUID userId);

    @Query("SELECT r.code FROM UserRole ur JOIN Role r ON ur.roleId = r.id WHERE ur.userId = :userId")
    List<String> findRoleCodesByUserId(UUID userId);

    long countByRoleId(UUID roleId);

    void deleteByUserId(UUID userId);
}
