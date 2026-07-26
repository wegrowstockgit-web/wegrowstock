package com.invsys.repository;

import com.invsys.domain.RtvOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RtvOrderLineRepository extends JpaRepository<RtvOrderLine, UUID> {

    List<RtvOrderLine> findByTenantIdAndRtvOrderId(UUID tenantId, UUID rtvOrderId);
}
