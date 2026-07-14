package com.invsys.service;

import com.invsys.api.dto.VanStockLevelResponse;
import com.invsys.api.dto.VehicleAssignmentResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Location;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.VehicleAssignment;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.VehicleAssignmentRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FieldFulfillmentService {

    private final VehicleAssignmentRepository assignmentRepository;
    private final LocationRepository locationRepository;
    private final InventoryLevelRepository levelRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;

    public FieldFulfillmentService(VehicleAssignmentRepository assignmentRepository,
                                   LocationRepository locationRepository,
                                   InventoryLevelRepository levelRepository,
                                   ProductVariantRepository variantRepository,
                                   InventoryService inventoryService) {
        this.assignmentRepository = assignmentRepository;
        this.locationRepository = locationRepository;
        this.levelRepository = levelRepository;
        this.variantRepository = variantRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public VehicleAssignmentResponse assignVehicle(UUID locationId, UUID technicianUserId) {
        UUID tenantId = TenantContext.requireTenantId();
        Location location = locationRepository.findById(locationId)
                .filter(l -> l.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Location not found"));
        if (!"VEHICLE".equals(location.getType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LOCATION",
                    "Assignment location must be type VEHICLE");
        }
        if (assignmentRepository.findByTenantIdAndTechnicianUserIdAndReturnedAtIsNull(tenantId, technicianUserId)
                .isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_ASSIGNMENT",
                    "Technician already has an active vehicle assignment");
        }

        VehicleAssignment assignment = new VehicleAssignment();
        assignment.setTenantId(tenantId);
        assignment.setLocationId(locationId);
        assignment.setTechnicianUserId(technicianUserId);
        assignment.setAssignedAt(Instant.now());
        return toResponse(assignmentRepository.save(assignment), location);
    }

    @Transactional
    public VehicleAssignmentResponse returnVehicle(UUID assignmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        VehicleAssignment assignment = assignmentRepository.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Assignment not found"));
        if (assignment.getReturnedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RETURNED", "Vehicle already returned");
        }
        assignment.setReturnedAt(Instant.now());
        Location location = locationRepository.findById(assignment.getLocationId()).orElse(null);
        return toResponse(assignmentRepository.save(assignment), location);
    }

    @Transactional(readOnly = true)
    public VehicleAssignmentResponse activeAssignmentForUser(UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();
        VehicleAssignment assignment = assignmentRepository
                .findByTenantIdAndTechnicianUserIdAndReturnedAtIsNull(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "No active vehicle assignment"));
        Location location = locationRepository.findById(assignment.getLocationId()).orElse(null);
        return toResponse(assignment, location);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<VehicleAssignment> findActiveAssignment(UUID tenantId, UUID userId) {
        return assignmentRepository.findByTenantIdAndTechnicianUserIdAndReturnedAtIsNull(tenantId, userId);
    }

    @Transactional
    public List<UUID> replenishVan(UUID fromWarehouseId, UUID toVehicleLocationId, Map<UUID, BigDecimal> items) {
        UUID tenantId = TenantContext.requireTenantId();
        Location vehicle = locationRepository.findById(toVehicleLocationId)
                .filter(l -> l.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Vehicle location not found"));
        if (!"VEHICLE".equals(vehicle.getType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LOCATION",
                    "toVehicleLocationId must be type VEHICLE");
        }
        if (items == null || items.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_ITEMS", "At least one item is required");
        }

        List<UUID> transferGroups = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : items.entrySet()) {
            if (entry.getValue() == null || entry.getValue().signum() <= 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_QTY", "Quantities must be positive");
            }
            UUID groupId = inventoryService.transfer(
                    entry.getKey(), fromWarehouseId, toVehicleLocationId, null, entry.getValue());
            transferGroups.add(groupId);
        }
        return transferGroups;
    }

    @Transactional
    public void consumeFromVan(UUID variantId, BigDecimal qty, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User required"));
        VehicleAssignment assignment = assignmentRepository
                .findByTenantIdAndTechnicianUserIdAndReturnedAtIsNull(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_VEHICLE",
                        "No active vehicle assignment for current user"));

        BigDecimal remaining = qty.abs();
        List<InventoryLevel> levels = levelRepository.findAvailableForAllocation(
                tenantId, variantId, List.of(assignment.getLocationId()));
        for (InventoryLevel level : levels) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = level.getAvailable().min(remaining);
            if (take.signum() <= 0) {
                continue;
            }
            inventoryService.consumeService(variantId, level.getLocationId(), level.getLotId(), take, reason);
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Insufficient van stock");
        }
    }

    @Transactional(readOnly = true)
    public List<VanStockLevelResponse> vanStock(UUID locationId) {
        UUID tenantId = TenantContext.requireTenantId();
        Location location = locationRepository.findById(locationId)
                .filter(l -> l.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Location not found"));
        if (!"VEHICLE".equals(location.getType())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LOCATION", "Location must be VEHICLE");
        }
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndLocationId(tenantId, locationId);
        Map<UUID, String> skus = variantRepository.findAllById(
                        levels.stream().map(InventoryLevel::getVariantId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getSku));
        return levels.stream()
                .map(l -> new VanStockLevelResponse(
                        l.getVariantId(),
                        skus.get(l.getVariantId()),
                        l.getLotId(),
                        l.getOnHand(),
                        l.getAllocated(),
                        l.getAvailable()))
                .toList();
    }

    private VehicleAssignmentResponse toResponse(VehicleAssignment assignment, Location location) {
        return new VehicleAssignmentResponse(
                assignment.getId(),
                assignment.getLocationId(),
                location != null ? location.getCode() : null,
                location != null ? location.getName() : null,
                assignment.getTechnicianUserId(),
                assignment.getAssignedAt(),
                assignment.getReturnedAt());
    }
}
