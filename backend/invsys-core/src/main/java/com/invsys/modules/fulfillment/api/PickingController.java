package com.invsys.modules.fulfillment.api;

import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.PickingTask;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.inventory.api.AllocationLookup;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.fulfillment.service.PickingService;
import com.invsys.service.PickingWaveService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/picking")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class PickingController {

    private final PickingWaveService pickingWaveService;
    private final AllocationLookup allocationRepository;
    private final ProductVariantRepository variantRepository;
    private final LocationRepository locationRepository;
    private final PickingService pickingService;

    public PickingController(PickingWaveService pickingWaveService,
                             AllocationLookup allocationRepository,
                             ProductVariantRepository variantRepository,
                             LocationRepository locationRepository,
                             PickingService pickingService) {
        this.pickingWaveService = pickingWaveService;
        this.allocationRepository = allocationRepository;
        this.variantRepository = variantRepository;
        this.locationRepository = locationRepository;
        this.pickingService = pickingService;
    }

    @PostMapping("/waves/generate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public GenerateWaveResponse generateWave(@RequestBody(required = false) GenerateWaveRequest request) {
        PickingWaveService.WaveResult result = pickingWaveService.generateWave(
                request != null ? request.assignedUserId() : null,
                request != null ? request.zoneId() : null);
        return toGenerateResponse(result);
    }

    /**
     * Surface B pick-path optimizer: returns a sequenced manifest ordered by BIN location path.
     */
    @PostMapping("/waves/optimize")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public OptimizeWaveResponse optimizeWave(@RequestBody(required = false) OptimizeWaveRequest request) {
        PickingWaveService.OptimizeResult result = pickingWaveService.optimizeWave(
                request != null ? request.salesOrderIds() : null);
        List<ManifestLineResponse> manifest = result.manifest().stream()
                .map(line -> new ManifestLineResponse(
                        line.sequenceOrder(),
                        line.taskId(),
                        line.allocationId(),
                        line.variantId(),
                        line.locationId(),
                        line.quantity(),
                        line.locationPath(),
                        line.pathSegments(),
                        resolveZone(line.locationPath())))
                .toList();
        return new OptimizeWaveResponse(result.waveId(), result.batchId(), result.status(), manifest);
    }

    @PostMapping("/waves/{waveId}/release")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public GenerateWaveResponse releaseWave(@PathVariable UUID waveId) {
        return toGenerateResponse(pickingWaveService.releaseWave(waveId));
    }

    @PostMapping("/waves/{waveId}/claim")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public ClaimWaveResponse claimWave(@PathVariable UUID waveId) {
        PickingWaveService.ClaimResult result = pickingWaveService.claimWave(waveId);
        return new ClaimWaveResponse(result.waveId(), result.assignedToUserId(), result.allocationsClaimed());
    }

    @GetMapping("/waves/{waveId}/picks")
    public List<PickResponse> wavePicks(@PathVariable UUID waveId) {
        return pickingWaveService.listPicksByPath(waveId).stream()
                .map(p -> new PickResponse(
                        p.taskId(),
                        p.allocationId(),
                        p.variantId(),
                        p.locationId(),
                        p.quantity(),
                        p.locationPath(),
                        resolveZone(p.locationPath()),
                        p.sequenceOrder(),
                        p.status()))
                .toList();
    }

    @GetMapping("/batches/current/tasks")
    public List<TaskResponse> currentTasks() {
        return pickingWaveService.currentBatchTasks().stream()
                .map(this::toTaskResponse)
                .toList();
    }

    @GetMapping("/wayfinding")
    public PickingService.WayfindingPath wayfinding(
            @RequestParam UUID fromLocationId,
            @RequestParam UUID toLocationId) {
        return pickingService.wayfinding(fromLocationId, toLocationId);
    }

    @PostMapping("/tasks/{taskId}/pick")
    public TaskResponse pickTask(@PathVariable UUID taskId) {
        return toTaskResponse(pickingWaveService.markTaskPicked(taskId));
    }

    private GenerateWaveResponse toGenerateResponse(PickingWaveService.WaveResult result) {
        List<TaskResponse> tasks = result.tasks().stream()
                .map(this::toTaskResponse)
                .toList();
        UUID batchId = result.batch() != null ? result.batch().getId() : null;
        return new GenerateWaveResponse(result.wave().getId(), batchId, result.wave().getStatus(), tasks);
    }

    private TaskResponse toTaskResponse(PickingTask task) {
        UUID variantId = null;
        UUID locationId = null;
        BigDecimal coordX = null;
        BigDecimal coordY = null;
        BigDecimal quantity = null;
        String sku = null;
        String barcode = null;
        boolean lotTracked = false;
        if (task.getAllocationId() != null) {
            Allocation allocation = allocationRepository.findById(task.getAllocationId()).orElse(null);
            if (allocation != null) {
                variantId = allocation.getVariantId();
                locationId = allocation.getLocationId();
                quantity = allocation.getQuantity();
                ProductVariant variant = variantId != null
                        ? variantRepository.findById(variantId).orElse(null)
                        : null;
                if (variant != null) {
                    lotTracked = variant.isLotTracked();
                    sku = variant.getSku();
                    barcode = variant.getBarcode();
                }
                if (locationId != null) {
                    Location loc = locationRepository.findById(locationId).orElse(null);
                    if (loc != null) {
                        coordX = loc.getCoordX();
                        coordY = loc.getCoordY();
                    }
                }
            }
        }
        return new TaskResponse(
                task.getId(),
                task.getAllocationId(),
                variantId,
                lotTracked,
                task.getLocationPath(),
                resolveZone(task.getLocationPath()),
                task.getSequenceOrder(),
                task.getStatus(),
                task.getToteIdentifier(),
                locationId,
                coordX,
                coordY,
                sku,
                barcode,
                quantity);
    }

    private static String resolveZone(String path) {
        if (path == null) {
            return "—";
        }
        String[] segments = path.split("/");
        return segments.length > 1 ? segments[1] : segments[0];
    }

    public record GenerateWaveRequest(UUID assignedUserId, UUID zoneId) {
    }

    public record GenerateWaveResponse(UUID waveId, UUID batchId, String status, List<TaskResponse> tasks) {
    }

    public record OptimizeWaveRequest(List<UUID> salesOrderIds) {
    }

    public record OptimizeWaveResponse(
            UUID waveId,
            UUID batchId,
            String status,
            List<ManifestLineResponse> manifest
    ) {
    }

    public record ManifestLineResponse(
            int sequenceOrder,
            UUID taskId,
            UUID allocationId,
            UUID variantId,
            UUID locationId,
            BigDecimal quantity,
            String locationPath,
            List<String> pathSegments,
            String zone
    ) {
    }

    public record ClaimWaveResponse(UUID waveId, UUID assignedToUserId, int allocationsClaimed) {
    }

    public record TaskResponse(
            UUID id,
            UUID allocationId,
            UUID variantId,
            boolean isLotTracked,
            String locationPath,
            String zone,
            int sequenceOrder,
            String status,
            String toteIdentifier,
            UUID locationId,
            BigDecimal coordX,
            BigDecimal coordY,
            String sku,
            String barcode,
            BigDecimal quantity
    ) {
    }

    public record PickResponse(
            UUID taskId,
            UUID allocationId,
            UUID variantId,
            UUID locationId,
            BigDecimal quantity,
            String locationPath,
            String zone,
            int sequenceOrder,
            String status
    ) {
    }
}
