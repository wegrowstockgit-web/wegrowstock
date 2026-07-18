package com.invsys.api;

import com.invsys.domain.Location;
import com.invsys.domain.WalkableEdge;
import com.invsys.repository.LocationRepository;
import com.invsys.service.PutAwaySuggestionService;
import com.invsys.service.SpatialMapService;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationRepository locationRepository;
    private final PutAwaySuggestionService putAwaySuggestionService;
    private final SpatialMapService spatialMapService;

    public LocationController(LocationRepository locationRepository,
                              PutAwaySuggestionService putAwaySuggestionService,
                              SpatialMapService spatialMapService) {
        this.locationRepository = locationRepository;
        this.putAwaySuggestionService = putAwaySuggestionService;
        this.spatialMapService = spatialMapService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<Location> list(@RequestParam(required = false) String type) {
        UUID tenantId = TenantContext.requireTenantId();
        List<Location> locations;
        if (type != null && !type.isBlank()) {
            locations = locationRepository.findByTenantIdAndType(tenantId, type);
        } else {
            locations = locationRepository.findByTenantIdOrderByPathAsc(tenantId);
        }

        // LBAC warehouse_ids apply to WAREHOUSE locations only — VEHICLE ids are never in the JWT claim.
        if ("WAREHOUSE".equalsIgnoreCase(type) && !isElevated()) {
            Set<UUID> allowed = TenantContext.getAuthorizedWarehouseIds().stream().collect(Collectors.toSet());
            return locations.stream().filter(loc -> allowed.contains(loc.getId())).toList();
        }
        return locations;
    }

    /**
     * LBAC-scoped warehouses for the active session (floor switcher bootstrap).
     * {@code /assigned} is the canonical path; {@code /allowed} is retained as an alias.
     */
    @GetMapping({"/warehouses/assigned", "/warehouses/allowed"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<Location> listAssignedWarehouses() {
        UUID tenantId = TenantContext.requireTenantId();
        List<Location> warehouses = locationRepository.findByTenantIdAndType(tenantId, "WAREHOUSE");
        if (isElevated()) {
            return warehouses;
        }
        Set<UUID> allowed = TenantContext.getAuthorizedWarehouseIds().stream().collect(Collectors.toSet());
        return warehouses.stream().filter(loc -> allowed.contains(loc.getId())).toList();
    }

    /**
     * Directed put-away: consolidate onto existing stock, else first empty BIN.
     */
    @GetMapping("/putaway-suggestions")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public PutAwaySuggestionService.PutAwaySuggestion putawaySuggestions(@RequestParam UUID variantId) {
        return putAwaySuggestionService.suggest(variantId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public Location create(@Valid @RequestBody CreateLocationRequest request) {
        Location location = new Location();
        location.setTenantId(TenantContext.requireTenantId());
        location.setParentLocationId(request.parentLocationId());
        location.setType(request.type());
        location.setCode(request.code());
        location.setName(request.name());
        location.setPath(request.path());
        if (request.zoneBehavior() != null && !request.zoneBehavior().isBlank()) {
            location.setZoneBehavior(request.zoneBehavior().trim().toUpperCase());
        }
        location.setCoordX(request.coordX());
        location.setCoordY(request.coordY());
        location.setCoordZ(request.coordZ());
        if (request.logisticsAddress() != null) {
            location.setLogisticsAddress(new LinkedHashMap<>(request.logisticsAddress()));
        }
        location.setGrossSquareFootage(request.grossSquareFootage());
        location.setOfficeAreaSquareFootage(request.officeAreaSquareFootage());
        location.setClearHeightFeet(request.clearHeightFeet());
        location.setTotalDockDoors(request.totalDockDoors());
        BigDecimal floorLoad = request.floorLoadCapacityLbs() != null
                ? request.floorLoadCapacityLbs()
                : request.weightCapacityLimit();
        location.setFloorLoadCapacityLbs(floorLoad);
        location.setWeightCapacityLimit(floorLoad);
        return locationRepository.save(location);
    }

    @PatchMapping("/{locationId}/coordinates")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public Location updateCoordinates(@PathVariable UUID locationId,
                                      @Valid @RequestBody UpdateCoordinatesRequest request) {
        return spatialMapService.updateCoordinates(
                locationId, request.coordX(), request.coordY(), request.coordZ());
    }

    @GetMapping("/heatmap")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<SpatialMapService.HeatmapCell> heatmap(@RequestParam(defaultValue = "7") int days) {
        return spatialMapService.heatmap(days);
    }

    @GetMapping("/walkable-edges")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<WalkableEdge> walkableEdges() {
        return spatialMapService.listEdges();
    }

    @PostMapping("/walkable-edges")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public WalkableEdge createWalkableEdge(@Valid @RequestBody CreateWalkableEdgeRequest request) {
        return spatialMapService.createEdge(request.nodeAId(), request.nodeBId(), request.distance());
    }

    private static boolean isElevated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_OWNER".equals(a) || "ROLE_ADMIN".equals(a));
    }

    public record CreateLocationRequest(
            UUID parentLocationId,
            @NotBlank String type,
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String path,
            String zoneBehavior,
            BigDecimal coordX,
            BigDecimal coordY,
            BigDecimal coordZ,
            Map<String, Object> logisticsAddress,
            BigDecimal grossSquareFootage,
            BigDecimal officeAreaSquareFootage,
            BigDecimal clearHeightFeet,
            Integer totalDockDoors,
            BigDecimal weightCapacityLimit,
            BigDecimal floorLoadCapacityLbs
    ) {
    }

    public record UpdateCoordinatesRequest(
            @NotNull BigDecimal coordX,
            @NotNull BigDecimal coordY,
            BigDecimal coordZ
    ) {
    }

    public record CreateWalkableEdgeRequest(
            @NotNull UUID nodeAId,
            @NotNull UUID nodeBId,
            BigDecimal distance
    ) {
    }
}
