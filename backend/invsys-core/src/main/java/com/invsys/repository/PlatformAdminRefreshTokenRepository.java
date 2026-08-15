package com.invsys.repository;

import com.invsys.domain.PlatformAdminRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformAdminRefreshTokenRepository extends JpaRepository<PlatformAdminRefreshToken, UUID> {
    Optional<PlatformAdminRefreshToken> findByTokenHash(String tokenHash);
}
