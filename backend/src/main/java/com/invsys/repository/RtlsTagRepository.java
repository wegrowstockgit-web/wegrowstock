package com.invsys.repository;

import com.invsys.domain.RtlsTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RtlsTagRepository extends JpaRepository<RtlsTag, UUID> {
    Optional<RtlsTag> findByTenantIdAndTagId(UUID tenantId, String tagId);

    List<RtlsTag> findByTenantIdAndActiveTrueOrderByTagIdAsc(UUID tenantId);
}
