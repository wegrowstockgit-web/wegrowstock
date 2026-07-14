package com.invsys.repository;

import com.invsys.domain.MediaObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaObjectRepository extends JpaRepository<MediaObject, UUID> {
    Optional<MediaObject> findByTenantIdAndId(UUID tenantId, UUID id);
}
