package com.invsys.repository;

import com.invsys.domain.LaborTimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LaborTimeEntryRepository extends JpaRepository<LaborTimeEntry, UUID> {

    List<LaborTimeEntry> findByTenantIdAndShiftIdOrderByStartedAtAsc(UUID tenantId, UUID shiftId);

    Optional<LaborTimeEntry> findByTenantIdAndUserIdAndEndedAtIsNull(UUID tenantId, UUID userId);
}
