package com.invsys.repository;

import com.invsys.domain.MediaAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaAttachmentRepository extends JpaRepository<MediaAttachment, UUID> {
    List<MediaAttachment> findByTenantIdAndEntityTypeAndEntityIdOrderBySortOrderAscCreatedAtAsc(
            UUID tenantId, String entityType, UUID entityId);
}
