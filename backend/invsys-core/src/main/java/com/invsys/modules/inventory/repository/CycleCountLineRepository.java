package com.invsys.modules.inventory.repository;

import com.invsys.modules.inventory.domain.CycleCountLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CycleCountLineRepository extends JpaRepository<CycleCountLine, UUID> {

    List<CycleCountLine> findByCycleCountIdOrderByCreatedAtAsc(UUID cycleCountId);

    List<CycleCountLine> findByTenantIdAndVarianceStatusOrderByUpdatedAtDesc(
            UUID tenantId, String varianceStatus);

    Optional<CycleCountLine> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByCycleCountIdAndVarianceStatus(UUID cycleCountId, String varianceStatus);
}
