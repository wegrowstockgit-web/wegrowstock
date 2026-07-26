package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.LaborShift;
import com.invsys.domain.LaborTimeEntry;
import com.invsys.repository.LaborShiftRepository;
import com.invsys.repository.LaborTimeEntryRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class LaborClockService {

    public static final Set<String> DIRECT_ACTIVITIES = Set.of("PICKING", "PUTAWAY", "CYCLE_COUNT");
    private static final String DEFAULT_START_ACTIVITY = "PICKING";

    private final LaborShiftRepository shiftRepository;
    private final LaborTimeEntryRepository timeEntryRepository;

    public LaborClockService(LaborShiftRepository shiftRepository,
                             LaborTimeEntryRepository timeEntryRepository) {
        this.shiftRepository = shiftRepository;
        this.timeEntryRepository = timeEntryRepository;
    }

    @Transactional
    public LaborStatus clockIn(UUID warehouseId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();

        shiftRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE")
                .ifPresent(shift -> {
                    throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CLOCKED_IN",
                            "User already has an active shift");
                });

        Instant now = Instant.now();
        LaborShift shift = new LaborShift();
        shift.setTenantId(tenantId);
        shift.setUserId(userId);
        shift.setWarehouseId(warehouseId);
        shift.setClockIn(now);
        shift.setStatus("ACTIVE");
        shift = shiftRepository.save(shift);

        LaborTimeEntry entry = startEntry(tenantId, userId, shift.getId(), DEFAULT_START_ACTIVITY, now);
        return toStatus(shift, entry);
    }

    @Transactional
    public LaborStatus clockOut() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        Instant now = Instant.now();

        LaborShift shift = shiftRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE")
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NOT_CLOCKED_IN",
                        "No active shift to clock out"));

        closeOpenEntry(tenantId, userId, now);
        shift.setClockOut(now);
        shift.setStatus("COMPLETED");
        shift = shiftRepository.save(shift);
        return toStatus(shift, null);
    }

    @Transactional
    public LaborStatus switchActivity(String activityType) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        Instant now = Instant.now();

        if (activityType == null || activityType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACTIVITY", "activityType is required");
        }
        String normalized = activityType.trim().toUpperCase();

        LaborShift shift = shiftRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE")
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NOT_CLOCKED_IN",
                        "Must be clocked in to switch activity"));

        closeOpenEntry(tenantId, userId, now);
        LaborTimeEntry entry = startEntry(tenantId, userId, shift.getId(), normalized, now);
        return toStatus(shift, entry);
    }

    @Transactional(readOnly = true)
    public LaborStatus currentStatus() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();

        return shiftRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE")
                .map(shift -> {
                    LaborTimeEntry open = timeEntryRepository
                            .findByTenantIdAndUserIdAndEndedAtIsNull(tenantId, userId)
                            .orElse(null);
                    return toStatus(shift, open);
                })
                .orElse(new LaborStatus(null, null, null, null, null, false));
    }

    @Transactional
    public void recordActivityUnit(UUID userId, int qty) {
        if (qty <= 0) {
            return;
        }
        UUID tenantId = TenantContext.requireTenantId();
        timeEntryRepository.findByTenantIdAndUserIdAndEndedAtIsNull(tenantId, userId)
                .filter(entry -> DIRECT_ACTIVITIES.contains(entry.getActivityType()))
                .ifPresent(entry -> {
                    entry.setUnitsProcessed(entry.getUnitsProcessed() + qty);
                    timeEntryRepository.save(entry);
                });
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary analyticsSummary() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();

        LaborShift shift = shiftRepository.findByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE")
                .orElse(null);
        if (shift == null) {
            List<LaborShift> recent = shiftRepository.findByTenantIdAndUserIdOrderByClockInDesc(tenantId, userId);
            shift = recent.isEmpty() ? null : recent.getFirst();
        }
        if (shift == null) {
            return new AnalyticsSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<LaborTimeEntry> entries = timeEntryRepository.findByTenantIdAndShiftIdOrderByStartedAtAsc(
                tenantId, shift.getId());
        Instant endBound = shift.getClockOut() != null ? shift.getClockOut() : Instant.now();

        BigDecimal directHours = BigDecimal.ZERO;
        BigDecimal indirectHours = BigDecimal.ZERO;
        int directUnits = 0;

        for (LaborTimeEntry entry : entries) {
            Instant entryEnd = entry.getEndedAt() != null ? entry.getEndedAt() : endBound;
            long seconds = Duration.between(entry.getStartedAt(), entryEnd).getSeconds();
            BigDecimal hours = BigDecimal.valueOf(seconds)
                    .divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP);

            if (DIRECT_ACTIVITIES.contains(entry.getActivityType())) {
                directHours = directHours.add(hours);
                directUnits += entry.getUnitsProcessed();
            } else {
                indirectHours = indirectHours.add(hours);
            }
        }

        BigDecimal uph = BigDecimal.ZERO;
        if (directHours.signum() > 0) {
            uph = BigDecimal.valueOf(directUnits).divide(directHours, 2, RoundingMode.HALF_UP);
        }

        BigDecimal totalHours = directHours.add(indirectHours);
        BigDecimal directPct = BigDecimal.ZERO;
        if (totalHours.signum() > 0) {
            directPct = directHours.multiply(BigDecimal.valueOf(100))
                    .divide(totalHours, 2, RoundingMode.HALF_UP);
        }

        return new AnalyticsSummary(uph, directHours, indirectHours, directPct,
                BigDecimal.valueOf(100).subtract(directPct));
    }

    private LaborTimeEntry startEntry(UUID tenantId, UUID userId, UUID shiftId, String activityType, Instant now) {
        LaborTimeEntry entry = new LaborTimeEntry();
        entry.setTenantId(tenantId);
        entry.setShiftId(shiftId);
        entry.setUserId(userId);
        entry.setActivityType(activityType);
        entry.setStartedAt(now);
        entry.setUnitsProcessed(0);
        return timeEntryRepository.save(entry);
    }

    private void closeOpenEntry(UUID tenantId, UUID userId, Instant now) {
        timeEntryRepository.findByTenantIdAndUserIdAndEndedAtIsNull(tenantId, userId)
                .ifPresent(entry -> {
                    entry.setEndedAt(now);
                    timeEntryRepository.save(entry);
                });
    }

    private static LaborStatus toStatus(LaborShift shift, LaborTimeEntry openEntry) {
        return new LaborStatus(
                shift.getId(),
                shift.getWarehouseId(),
                shift.getClockIn(),
                shift.getClockOut(),
                openEntry != null ? openEntry.getActivityType() : null,
                "ACTIVE".equalsIgnoreCase(shift.getStatus()));
    }

    public record LaborStatus(
            UUID shiftId,
            UUID warehouseId,
            Instant clockIn,
            Instant clockOut,
            String currentActivity,
            boolean active
    ) {
    }

    public record AnalyticsSummary(
            BigDecimal unitsPerHour,
            BigDecimal directHours,
            BigDecimal indirectHours,
            BigDecimal directPercent,
            BigDecimal indirectPercent
    ) {
    }
}
