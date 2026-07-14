package com.invsys.service;

import com.invsys.domain.Allocation;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.Location;
import com.invsys.repository.InventoryLevelRepository;
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
 * approximation over warehouse location paths (TSP heuristic), biased by
 * {@code locations.sequence_index} for spatial locality.
 */
@Service
public class PickingService {

    private final InventoryLevelRepository levelRepository;

    public PickingService(InventoryLevelRepository levelRepository) {
        this.levelRepository = levelRepository;
    }

    public List<Allocation> optimizePickSequence(List<Allocation> allocations, Map<UUID, String> locationPaths) {
        return optimizePickSequence(allocations, locationPaths, Map.of());
    }

    public List<Allocation> optimizePickSequence(List<Allocation> allocations,
                                                 Map<UUID, String> locationPaths,
                                                 Map<UUID, Integer> sequenceIndexes) {
        if (allocations.size() <= 1) {
            return List.copyOf(allocations);
        }

        Map<UUID, PathCoordinate> coordinates = new HashMap<>();
        for (Allocation allocation : allocations) {
            String path = locationPaths.getOrDefault(allocation.getLocationId(), "ZZZZ");
            int seq = sequenceIndexes.getOrDefault(allocation.getLocationId(), 0);
            coordinates.put(allocation.getId(), PathCoordinate.fromPath(path, seq));
        }

        List<Allocation> remaining = new ArrayList<>(allocations);
        remaining.sort(Comparator
                .comparing((Allocation a) -> sequenceIndexes.getOrDefault(a.getLocationId(), Integer.MAX_VALUE))
                .thenComparing(a -> locationPaths.getOrDefault(a.getLocationId(), "ZZZZ")));

        List<Allocation> route = new ArrayList<>();
        Allocation current = remaining.removeFirst();
        route.add(current);

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
     * Re-sequence pending pick tasks by minimizing travel across location paths / sequence_index.
     */
    public List<String> optimizePendingPaths(List<String> pendingPaths, Map<String, Integer> pathToSequenceIndex) {
        if (pendingPaths.size() <= 1) {
            return List.copyOf(pendingPaths);
        }

        List<PathNode> remaining = new ArrayList<>();
        for (int i = 0; i < pendingPaths.size(); i++) {
            String path = pendingPaths.get(i);
            remaining.add(new PathNode(i, path, PathCoordinate.fromPath(
                    path, pathToSequenceIndex.getOrDefault(path, i))));
        }
        remaining.sort(Comparator
                .comparingInt((PathNode n) -> pathToSequenceIndex.getOrDefault(n.path(), Integer.MAX_VALUE))
                .thenComparing(PathNode::path));

        List<String> route = new ArrayList<>();
        PathNode current = remaining.removeFirst();
        route.add(current.path());
        Set<Integer> visited = new HashSet<>();
        visited.add(current.index());

        while (!remaining.isEmpty()) {
            PathNode nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (PathNode candidate : remaining) {
                double distance = current.coord().distanceTo(candidate.coord());
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
            route.add(nearest.path());
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

    private record PathNode(int index, String path, PathCoordinate coord) {
    }

    private record PathCoordinate(int warehouse, int zone, int aisle, int bin, int sequenceIndex) {

        static PathCoordinate fromPath(String path, int sequenceIndex) {
            String[] parts = path.split("/");
            return new PathCoordinate(
                    hash(parts, 0),
                    hash(parts, 1),
                    hash(parts, 2),
                    hash(parts, 3),
                    sequenceIndex
            );
        }

        private static int hash(String[] parts, int index) {
            if (index >= parts.length) {
                return 0;
            }
            String part = parts[index];
            // Prefer trailing digits (AISLE-12 / BIN-03) for natural aisle/shelf walk order
            int digits = 0;
            boolean foundDigit = false;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c >= '0' && c <= '9') {
                    digits = digits * 10 + (c - '0');
                    foundDigit = true;
                } else if (foundDigit) {
                    break;
                }
            }
            if (foundDigit) {
                return digits;
            }
            return part.chars().sum();
        }

        double distanceTo(PathCoordinate other) {
            // Prefer sequence_index locality (warehouse walk order) then hierarchical path distance
            // zone → aisle → shelf/bin elevation — S-shape aisle bias.
            double seqDelta = Math.abs(sequenceIndex - other.sequenceIndex);
            return seqDelta * 50
                    + Math.abs(warehouse - other.warehouse) * 1000
                    + Math.abs(zone - other.zone) * 100
                    + Math.abs(aisle - other.aisle) * 10
                    + Math.abs(bin - other.bin);
        }
    }
}
