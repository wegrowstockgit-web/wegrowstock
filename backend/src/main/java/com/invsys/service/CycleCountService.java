package com.invsys.service;

import com.invsys.domain.CycleCount;
import com.invsys.domain.Location;
import com.invsys.repository.CycleCountRepository;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CycleCountService {

    private static final int VELOCITY_THRESHOLD = 25;
    private static final int NEGATIVE_ADJUST_THRESHOLD = 3;

    private final InventoryLedgerRepository ledgerRepository;
    private final CycleCountRepository cycleCountRepository;
    private final LocationRepository locationRepository;

    public CycleCountService(InventoryLedgerRepository ledgerRepository,
                             CycleCountRepository cycleCountRepository,
                             LocationRepository locationRepository) {
        this.ledgerRepository = ledgerRepository;
        this.cycleCountRepository = cycleCountRepository;
        this.locationRepository = locationRepository;
    }

    public List<PriorityAudit> priorityAudits() {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, String> locationPaths = locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(Location::getId, Location::getPath, (a, b) -> a));

        return cycleCountRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "IN_PROGRESS").stream()
                .map(count -> new PriorityAudit(
                        count.getId(),
                        count.getLocationId(),
                        locationPaths.getOrDefault(count.getLocationId(), "—"),
                        count.getNotes(),
                        count.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void evaluateLocationVelocity(UUID locationId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (cycleCountRepository.existsByTenantIdAndLocationIdAndStatus(tenantId, locationId, "IN_PROGRESS")) {
            return;
        }
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);

        long movementCount = 0;
        BigDecimal movementVolume = BigDecimal.ZERO;
        for (Object[] row : ledgerRepository.movementStatsByLocationSince(tenantId, since)) {
            UUID locId = (UUID) row[0];
            if (!locId.equals(locationId)) {
                continue;
            }
            movementCount = ((Number) row[1]).longValue();
            movementVolume = (BigDecimal) row[2];
            break;
        }

        long negativeAdjusts = 0;
        for (Object[] row : ledgerRepository.negativeAdjustCountsByLocationSince(tenantId, since)) {
            UUID locId = (UUID) row[0];
            if (locId.equals(locationId)) {
                negativeAdjusts = ((Number) row[1]).longValue();
                break;
            }
        }

        boolean highVelocity = movementCount >= VELOCITY_THRESHOLD
                || movementVolume.compareTo(BigDecimal.valueOf(100)) >= 0;
        boolean frequentNegativeAdjusts = negativeAdjusts >= NEGATIVE_ADJUST_THRESHOLD;

        if (!highVelocity && !frequentNegativeAdjusts) {
            return;
        }

        CycleCount count = new CycleCount();
        count.setTenantId(tenantId);
        count.setLocationId(locationId);
        count.setStatus("IN_PROGRESS");
        count.setNotes(highVelocity
                ? "Priority audit: high movement velocity detected"
                : "Priority audit: frequent negative adjustments detected");
        cycleCountRepository.save(count);
    }

    public record PriorityAudit(
            UUID id,
            UUID locationId,
            String locationPath,
            String notes,
            Instant createdAt
    ) {
    }
}
