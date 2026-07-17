package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.CycleCount;
import com.invsys.domain.CycleCountLine;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Location;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.CycleCountLineRepository;
import com.invsys.repository.CycleCountRepository;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CycleCountService {

    public static final String VARIANCE_PENDING = "PENDING";
    public static final String VARIANCE_AUTO_APPROVED = "AUTO_APPROVED";
    public static final String VARIANCE_PENDING_MANAGER_REVIEW = "PENDING_MANAGER_REVIEW";
    public static final String VARIANCE_APPROVED = "APPROVED";
    public static final String VARIANCE_RECOUNT_REQUESTED = "RECOUNT_REQUESTED";

    private static final int VELOCITY_THRESHOLD = 25;
    private static final int NEGATIVE_ADJUST_THRESHOLD = 3;
    private static final String REASON_CYCLE_COUNT = "CYCLE_COUNT";

    private final InventoryLedgerRepository ledgerRepository;
    private final CycleCountRepository cycleCountRepository;
    private final CycleCountLineRepository cycleCountLineRepository;
    private final LocationRepository locationRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final ProductVariantRepository productVariantRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final InventoryService inventoryService;

    public CycleCountService(InventoryLedgerRepository ledgerRepository,
                             CycleCountRepository cycleCountRepository,
                             CycleCountLineRepository cycleCountLineRepository,
                             LocationRepository locationRepository,
                             InventoryLevelRepository inventoryLevelRepository,
                             ProductVariantRepository productVariantRepository,
                             TenantSettingsRepository tenantSettingsRepository,
                             @Lazy InventoryService inventoryService) {
        this.ledgerRepository = ledgerRepository;
        this.cycleCountRepository = cycleCountRepository;
        this.cycleCountLineRepository = cycleCountLineRepository;
        this.locationRepository = locationRepository;
        this.inventoryLevelRepository = inventoryLevelRepository;
        this.productVariantRepository = productVariantRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.inventoryService = inventoryService;
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

    public BlindCountSettings blindCountSettings() {
        TenantSettings settings = requireSettings();
        return new BlindCountSettings(settings.isBlindCycleCounts(), settings.getMaxAutoAdjustValue());
    }

    @Transactional
    public CycleCountDetail startCount(UUID locationId) {
        UUID tenantId = TenantContext.requireTenantId();
        Location location = locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Location not found"));

        if (cycleCountRepository.existsByTenantIdAndLocationIdAndStatus(tenantId, locationId, "IN_PROGRESS")) {
            CycleCount existing = cycleCountRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "IN_PROGRESS")
                    .stream()
                    .filter(c -> locationId.equals(c.getLocationId()))
                    .findFirst()
                    .orElseThrow();
            List<CycleCountLine> existingLines =
                    cycleCountLineRepository.findByCycleCountIdOrderByCreatedAtAsc(existing.getId());
            boolean hasWorkerOpenLines = existingLines.stream()
                    .anyMatch(l -> VARIANCE_PENDING.equals(l.getVarianceStatus())
                            || VARIANCE_RECOUNT_REQUESTED.equals(l.getVarianceStatus()));
            if (hasWorkerOpenLines || existingLines.isEmpty()) {
                return openCount(existing.getId());
            }
            // Manager-review / approved lines only — close header so a fresh snapshot can start.
            // Pending variances remain queryable by variance_status on the completed count.
            existing.setStatus("COMPLETED");
            cycleCountRepository.save(existing);
        }

        CycleCount count = new CycleCount();
        count.setTenantId(tenantId);
        count.setLocationId(location.getId());
        count.setStatus("IN_PROGRESS");
        count.setNotes("Manual cycle count");
        count.setCreatedBy(TenantContext.getUserId().orElse(null));
        count = cycleCountRepository.save(count);
        snapshotLines(count);
        return toDetail(count);
    }

    @Transactional
    public CycleCountDetail openCount(UUID cycleCountId) {
        CycleCount count = requireCount(cycleCountId);
        if (!"IN_PROGRESS".equals(count.getStatus()) && !"DRAFT".equals(count.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COUNT_NOT_OPEN",
                    "Cycle count is not open for counting");
        }
        if ("DRAFT".equals(count.getStatus())) {
            count.setStatus("IN_PROGRESS");
            cycleCountRepository.save(count);
        }
        List<CycleCountLine> lines = cycleCountLineRepository.findByCycleCountIdOrderByCreatedAtAsc(count.getId());
        if (lines.isEmpty()) {
            snapshotLines(count);
        } else {
            sanitizeInvalidPendingLines(lines);
        }
        return toDetail(count);
    }

    public CycleCountDetail getCount(UUID cycleCountId) {
        return toDetail(requireCount(cycleCountId));
    }

    /**
     * Blind-count submission with automated variance escalation (Rules A/B/C).
     */
    @Transactional
    public CycleCountLineView submitCountedQty(UUID cycleCountId, UUID lineId, BigDecimal countedQty) {
        if (countedQty == null || countedQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COUNTED_QTY",
                    "counted_qty must be a non-negative number");
        }

        CycleCount count = requireCount(cycleCountId);
        if (!"IN_PROGRESS".equals(count.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "COUNT_NOT_OPEN",
                    "Cycle count is not open for counting");
        }

        UUID tenantId = TenantContext.requireTenantId();
        CycleCountLine line = cycleCountLineRepository.findByIdAndTenantId(lineId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LINE_NOT_FOUND", "Count line not found"));
        if (!count.getId().equals(line.getCycleCountId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LINE_NOT_FOUND", "Count line not found on this count");
        }
        if (!VARIANCE_PENDING.equals(line.getVarianceStatus())
                && !VARIANCE_RECOUNT_REQUESTED.equals(line.getVarianceStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "LINE_ALREADY_SUBMITTED",
                    "This line has already been submitted");
        }

        ProductVariant variant = productVariantRepository.findById(line.getVariantId())
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variant not found"));

        BigDecimal expected = line.getExpectedQty() != null ? line.getExpectedQty() : BigDecimal.ZERO;
        BigDecimal deltaQty = expected.subtract(countedQty).abs();
        BigDecimal avgCost = variant.getAvgCost() != null ? variant.getAvgCost() : BigDecimal.ZERO;
        BigDecimal financialImpact = deltaQty.multiply(avgCost).setScale(4, RoundingMode.HALF_UP);

        line.setCountedQty(countedQty);
        line.setFinancialImpact(financialImpact);

        TenantSettings settings = requireSettings();
        BigDecimal threshold = settings.getMaxAutoAdjustValue() != null
                ? settings.getMaxAutoAdjustValue()
                : new BigDecimal("100.00");

        if (deltaQty.compareTo(BigDecimal.ZERO) == 0) {
            // Rule A — exact match
            line.setVarianceStatus(VARIANCE_AUTO_APPROVED);
            cycleCountLineRepository.save(line);
            maybeCompleteCount(count);
            return toLineView(line, variant.getSku(), null);
        }

        if (financialImpact.compareTo(threshold) < 0) {
            // Rule B — under threshold: auto-approve + ledger adjust
            BigDecimal ledgerDelta = countedQty.subtract(expected);
            if (ledgerDelta.compareTo(BigDecimal.ZERO) != 0) {
                inventoryService.adjust(
                        line.getVariantId(),
                        count.getLocationId(),
                        line.getLotId(),
                        ledgerDelta,
                        REASON_CYCLE_COUNT);
            }
            line.setVarianceStatus(VARIANCE_AUTO_APPROVED);
            cycleCountLineRepository.save(line);
            maybeCompleteCount(count);
            return toLineView(line, variant.getSku(), null);
        }

        // Rule C — escalate; do not touch ledger
        line.setVarianceStatus(VARIANCE_PENDING_MANAGER_REVIEW);
        cycleCountLineRepository.save(line);
        return toLineView(line, variant.getSku(), null);
    }

    public List<PendingVariance> pendingVariances() {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, String> locationPaths = locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .collect(java.util.stream.Collectors.toMap(Location::getId, Location::getPath, (a, b) -> a));
        Map<UUID, String> skus = new HashMap<>();

        List<PendingVariance> result = new ArrayList<>();
        for (CycleCountLine line : cycleCountLineRepository.findByTenantIdAndVarianceStatusOrderByUpdatedAtDesc(
                tenantId, VARIANCE_PENDING_MANAGER_REVIEW)) {
            CycleCount count = cycleCountRepository.findByIdAndTenantId(line.getCycleCountId(), tenantId)
                    .orElse(null);
            if (count == null) {
                continue;
            }
            String sku = skus.computeIfAbsent(line.getVariantId(), id ->
                    productVariantRepository.findById(id).map(ProductVariant::getSku).orElse("—"));
            BigDecimal expected = line.getExpectedQty() != null ? line.getExpectedQty() : BigDecimal.ZERO;
            BigDecimal counted = line.getCountedQty() != null ? line.getCountedQty() : BigDecimal.ZERO;
            BigDecimal impact = line.getFinancialImpact() != null
                    ? line.getFinancialImpact()
                    : BigDecimal.ZERO;
            result.add(new PendingVariance(
                    line.getId(),
                    count.getId(),
                    count.getLocationId(),
                    locationPaths.getOrDefault(count.getLocationId(), "—"),
                    line.getVariantId(),
                    sku,
                    expected,
                    counted,
                    impact,
                    line.getVarianceStatus(),
                    line.getUpdatedAt()));
        }
        return result;
    }

    @Transactional
    public CycleCountLineView approveLedgerAdjustment(UUID lineId) {
        UUID tenantId = TenantContext.requireTenantId();
        CycleCountLine line = cycleCountLineRepository.findByIdAndTenantId(lineId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LINE_NOT_FOUND", "Count line not found"));
        if (!VARIANCE_PENDING_MANAGER_REVIEW.equals(line.getVarianceStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING_REVIEW",
                    "Line is not awaiting manager review");
        }
        CycleCount count = requireCount(line.getCycleCountId());
        BigDecimal expected = line.getExpectedQty() != null ? line.getExpectedQty() : BigDecimal.ZERO;
        BigDecimal counted = line.getCountedQty() != null ? line.getCountedQty() : BigDecimal.ZERO;
        BigDecimal ledgerDelta = counted.subtract(expected);
        if (ledgerDelta.compareTo(BigDecimal.ZERO) != 0) {
            inventoryService.adjust(
                    line.getVariantId(),
                    count.getLocationId(),
                    line.getLotId(),
                    ledgerDelta,
                    REASON_CYCLE_COUNT);
        }
        line.setVarianceStatus(VARIANCE_APPROVED);
        cycleCountLineRepository.save(line);
        maybeCompleteCount(count);
        String sku = productVariantRepository.findById(line.getVariantId())
                .map(ProductVariant::getSku).orElse("—");
        return toLineView(line, sku, locationPath(count.getLocationId()));
    }

    @Transactional
    public CycleCountLineView requestRecount(UUID lineId) {
        UUID tenantId = TenantContext.requireTenantId();
        CycleCountLine line = cycleCountLineRepository.findByIdAndTenantId(lineId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LINE_NOT_FOUND", "Count line not found"));
        if (!VARIANCE_PENDING_MANAGER_REVIEW.equals(line.getVarianceStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING_REVIEW",
                    "Line is not awaiting manager review");
        }
        CycleCount count = requireCount(line.getCycleCountId());
        line.setCountedQty(null);
        line.setFinancialImpact(null);
        line.setVarianceStatus(VARIANCE_PENDING);
        cycleCountLineRepository.save(line);

        count.setStatus("IN_PROGRESS");
        String notes = count.getNotes() == null ? "" : count.getNotes();
        if (!notes.contains("Recount requested")) {
            count.setNotes((notes.isBlank() ? "" : notes + " — ") + "Recount requested");
        }
        cycleCountRepository.save(count);
        String sku = productVariantRepository.findById(line.getVariantId())
                .map(ProductVariant::getSku).orElse("—");
        return toLineView(line, sku, locationPath(count.getLocationId()));
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

    private void snapshotLines(CycleCount count) {
        UUID tenantId = count.getTenantId();
        List<InventoryLevel> levels = inventoryLevelRepository.findByTenantIdAndLocationId(
                tenantId, count.getLocationId());
        for (InventoryLevel level : levels) {
            if (level.getOnHand() == null || level.getOnHand().signum() <= 0) {
                continue;
            }
            CycleCountLine line = new CycleCountLine();
            line.setTenantId(tenantId);
            line.setCycleCountId(count.getId());
            line.setVariantId(level.getVariantId());
            line.setLotId(level.getLotId());
            line.setExpectedQty(level.getOnHand());
            line.setVarianceStatus(VARIANCE_PENDING);
            cycleCountLineRepository.save(line);
        }
    }

    /** Drop non-countable pending lines (e.g. negative on-hand snapshots from prior data). */
    private void sanitizeInvalidPendingLines(List<CycleCountLine> lines) {
        for (CycleCountLine line : lines) {
            if (!VARIANCE_PENDING.equals(line.getVarianceStatus())
                    && !VARIANCE_RECOUNT_REQUESTED.equals(line.getVarianceStatus())) {
                continue;
            }
            BigDecimal expected = line.getExpectedQty() != null ? line.getExpectedQty() : BigDecimal.ZERO;
            if (expected.signum() < 0) {
                line.setCountedQty(BigDecimal.ZERO);
                line.setFinancialImpact(BigDecimal.ZERO);
                line.setVarianceStatus(VARIANCE_AUTO_APPROVED);
                cycleCountLineRepository.save(line);
            }
        }
    }

    private void maybeCompleteCount(CycleCount count) {
        List<CycleCountLine> lines = cycleCountLineRepository.findByCycleCountIdOrderByCreatedAtAsc(count.getId());
        if (lines.isEmpty()) {
            return;
        }
        boolean allDone = lines.stream().allMatch(l ->
                VARIANCE_AUTO_APPROVED.equals(l.getVarianceStatus())
                        || VARIANCE_APPROVED.equals(l.getVarianceStatus()));
        if (allDone) {
            count.setStatus("COMPLETED");
            cycleCountRepository.save(count);
        }
    }

    private CycleCount requireCount(UUID cycleCountId) {
        UUID tenantId = TenantContext.requireTenantId();
        return cycleCountRepository.findByIdAndTenantId(cycleCountId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COUNT_NOT_FOUND", "Cycle count not found"));
    }

    private TenantSettings requireSettings() {
        UUID tenantId = TenantContext.requireTenantId();
        return tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantSettings created = TenantSettings.withDefaults(tenantId);
                    return tenantSettingsRepository.save(created);
                });
    }

    private String locationPath(UUID locationId) {
        return locationRepository.findById(locationId).map(Location::getPath).orElse("—");
    }

    private CycleCountDetail toDetail(CycleCount count) {
        BlindCountSettings settings = blindCountSettings();
        List<CycleCountLine> lines = cycleCountLineRepository.findByCycleCountIdOrderByCreatedAtAsc(count.getId());
        List<CycleCountLineView> views = new ArrayList<>();
        for (CycleCountLine line : lines) {
            String sku = productVariantRepository.findById(line.getVariantId())
                    .map(ProductVariant::getSku).orElse("—");
            views.add(toLineView(line, sku, null));
        }
        return new CycleCountDetail(
                count.getId(),
                count.getLocationId(),
                locationPath(count.getLocationId()),
                count.getStatus(),
                count.getNotes(),
                settings.blindCycleCounts(),
                settings.maxAutoAdjustValue(),
                views);
    }

    private CycleCountLineView toLineView(CycleCountLine line, String sku, String locationPath) {
        return new CycleCountLineView(
                line.getId(),
                line.getCycleCountId(),
                line.getVariantId(),
                sku,
                locationPath,
                line.getLotId(),
                line.getExpectedQty(),
                line.getCountedQty(),
                line.getVarianceStatus(),
                line.getFinancialImpact());
    }

    public record PriorityAudit(
            UUID id,
            UUID locationId,
            String locationPath,
            String notes,
            Instant createdAt
    ) {
    }

    public record BlindCountSettings(
            boolean blindCycleCounts,
            BigDecimal maxAutoAdjustValue
    ) {
    }

    public record CycleCountDetail(
            UUID id,
            UUID locationId,
            String locationPath,
            String status,
            String notes,
            boolean blindCycleCounts,
            BigDecimal maxAutoAdjustValue,
            List<CycleCountLineView> lines
    ) {
    }

    public record CycleCountLineView(
            UUID id,
            UUID cycleCountId,
            UUID variantId,
            String sku,
            String locationPath,
            UUID lotId,
            BigDecimal expectedQty,
            BigDecimal countedQty,
            String varianceStatus,
            BigDecimal financialImpact
    ) {
    }

    public record PendingVariance(
            UUID lineId,
            UUID cycleCountId,
            UUID locationId,
            String locationPath,
            UUID variantId,
            String sku,
            BigDecimal expectedQty,
            BigDecimal countedQty,
            BigDecimal financialDelta,
            String varianceStatus,
            Instant updatedAt
    ) {
    }
}
