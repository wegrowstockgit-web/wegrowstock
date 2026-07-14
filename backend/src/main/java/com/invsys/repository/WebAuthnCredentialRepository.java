package com.invsys.repository;

import com.invsys.domain.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, UUID> {
    Optional<WebAuthnCredential> findByTenantIdAndCredentialId(UUID tenantId, String credentialId);

    List<WebAuthnCredential> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
