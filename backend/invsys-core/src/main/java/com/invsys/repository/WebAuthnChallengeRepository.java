package com.invsys.repository;

import com.invsys.domain.WebAuthnChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebAuthnChallengeRepository extends JpaRepository<WebAuthnChallenge, UUID> {
    Optional<WebAuthnChallenge> findByTenantIdAndChallenge(UUID tenantId, String challenge);
}
