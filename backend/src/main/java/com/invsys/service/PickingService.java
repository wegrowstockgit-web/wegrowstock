package com.invsys.service;

import com.invsys.domain.Allocation;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Location;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Intelligent path optimization for wave/zone picking using a nearest-neighbor
 * approximation over warehouse location paths (TSP heuristic).
 */
@Service
public class PickingService {

    private final InventoryLevelRepository levelRepository;

    public PickingService(InventoryLevelRepository levelRepository) {
        this.levelRepository = levelRepository;
    }

    public List<Allocation> optimizePickSequence(List<Allocation> allocations, Map<UUID, String> locationPaths) {
        if (allocations.size() <= 1) {
            return List.copyOf(allocations);
        }

        Map<UUID, PathCoordinate> coordinates = new HashMap<>();
        for (Allocation allocation : allocations) {
            String path = locationPaths.getOrDefault(allocation.getLocationId(), "ZZZZ");
            coordinates.put(allocation.getId(), PathCoordinate.fromPath(path));
        }

        List<Allocation> remaining = new ArrayList<>(allocations);
        remaining.sort(Comparator.comparing(a -> locationPaths.getOrDefault(a.getLocationId(), "ZZZZ")));

        List<Allocation> route = new ArrayList<>();
        Allocation current = remaining.removeFirst();
        route.add(current);
        Set<UUID> visited = new HashSet<>();
        visited.add(current.getId());

        while (!remaining.isEmpty()) {
            PathCoordinate currentCoord = coordinates.get(current.getId());
            Allocation nearest = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Allocation candidate : remaining) {
                double distance = currentCoord.distanceTo(coordinates.get(candidate.getId()));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
            }

            if (nearest == null) {
                nearest = remaining.removeFirst();
            } else {
                remaining.remove(nearest);
            }
            route.add(nearest);
            current = nearest;
        }

        return route;
    }

    /**
     * Pessimistic lock inventory_levels rows backing the pick route (SKIP LOCKED).
     */
    public List<InventoryLevel> lockLevelsForRoute(UUID tenantId, List<Allocation> route) {
        List<InventoryLevel> locked = new ArrayList<>();
        for (Allocation allocation : route) {
            levelRepository.lockLevelForAllocation(
                            tenantId,
                            allocation.getVariantId(),
                            allocation.getLocationId(),
                            allocation.getLotId())
                    .ifPresent(locked::add);
        }
        return locked;
    }

    public List<PickingStop> buildStops(List<Allocation> route, Map<UUID, Location> locationsById) {
        List<PickingStop> stops = new ArrayList<>();
        int sequence = 1;
        for (Allocation allocation : route) {
            Location location = locationsById.get(allocation.getLocationId());
            String path = location != null ? location.getPath() : "UNKNOWN";
            String zone = resolveZone(location);
            stops.add(new PickingStop(
                    allocation.getId(),
                    path,
                    zone,
                    sequence++,
                    allocation.getQuantity()
            ));
        }
        return stops;
    }

    private static String resolveZone(Location location) {
        if (location == null || location.getPath() == null) {
            return "—";
        }
        String[] segments = location.getPath().split("/");
        return segments.length > 1 ? segments[1] : segments[0];
    }

    public record PickingStop(
            UUID allocationId,
            String locationPath,
            String zone,
            int sequenceOrder,
            java.math.BigDecimal quantity
    ) {
    }

    private record PathCoordinate(int warehouse, int zone, int aisle, int bin) {

        static PathCoordinate fromPath(String path) {
            String[] parts = path.split("/");
            return new PathCoordinate(
                    hash(parts, 0),
                    hash(parts, 1),
                    hash(parts, 2),
                    hash(parts, 3)
            );
        }

        private static int hash(String[] parts, int index) {
            if (index >= parts.length) {
                return 0;
            }
            return parts[index].chars().sum();
        }

        double distanceTo(PathCoordinate other) {
            return Math.abs(warehouse - other.warehouse) * 1000
                    + Math.abs(zone - other.zone) * 100
                    + Math.abs(aisle - other.aisle) * 10
                    + Math.abs(bin - other.bin);
        }
    }
}
