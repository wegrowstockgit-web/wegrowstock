package com.invsys.repository;

import com.invsys.domain.RtlsPositionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RtlsPositionEventRepository extends JpaRepository<RtlsPositionEvent, UUID> {
    List<RtlsPositionEvent> findTop100ByTenantIdOrderByObservedAtDesc(UUID tenantId);
}
