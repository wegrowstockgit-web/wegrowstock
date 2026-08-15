package com.invsys.repository;

import com.invsys.domain.PlatformAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {
    Optional<PlatformAdmin> findByEmailIgnoreCase(String email);
}
