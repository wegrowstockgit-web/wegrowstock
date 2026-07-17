package com.invsys.repository;

import com.invsys.domain.UserSavedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSavedViewRepository extends JpaRepository<UserSavedView, UUID> {

    List<UserSavedView> findByTenantIdAndUserIdAndGridIdentifierOrderByCreatedAtAsc(
            UUID tenantId, UUID userId, String gridIdentifier);

    Optional<UserSavedView> findByTenantIdAndUserIdAndGridIdentifierAndName(
            UUID tenantId, UUID userId, String gridIdentifier, String name);

    Optional<UserSavedView> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);
}
