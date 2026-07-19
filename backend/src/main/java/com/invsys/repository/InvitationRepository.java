package com.invsys.repository;

import com.invsys.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByTokenHash(String tokenHash);

    Optional<Invitation> findByTenantIdAndIdAndAcceptedAtIsNull(UUID tenantId, UUID id);

    List<Invitation> findByTenantIdAndAcceptedAtIsNullOrderByExpiresAtAsc(UUID tenantId);

    boolean existsByTenantIdAndEmailIgnoreCaseAndAcceptedAtIsNull(UUID tenantId, String email);
}
