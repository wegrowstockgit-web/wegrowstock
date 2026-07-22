package com.invsys.repository;

import com.invsys.domain.MagicLoginToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MagicLoginTokenRepository extends JpaRepository<MagicLoginToken, UUID> {
    Optional<MagicLoginToken> findByTokenHash(String tokenHash);
}
