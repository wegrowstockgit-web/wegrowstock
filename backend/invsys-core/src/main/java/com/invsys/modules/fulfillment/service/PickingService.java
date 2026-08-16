package com.invsys.modules.fulfillment.service;

import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.WalkableEdge;
import com.invsys.modules.inventory.api.InventoryLevelLookup;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.WalkableEdgeRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hierarchical location-path pick sequencing (Warehouse → Zone → Aisle → Bay → Shelf)
 * plus A* wayfinding over the Digital Twin walkable graph for scanner navigation.
 */
@Service
public class PickingService {

    private final InventoryLevelLookup levelRepository;
    private final LocationRepository locationRepository;
    private final WalkableEdgeRepository walkableEdgeRepository;
    private final ProductVariantRepository variantRepository;

    public PickingService(InventoryLevelLookup levelRepository,
                          LocationRepository locationRepository,
                          WalkableEdgeRepository walkableEdgeRepository,
                          ProductVariantRepository variantRepository) {
        this.levelRepository = levelRepository;
        this.locationRepository = locationRepository;
        this.walkableEdgeRepository = walkableEdgeRepository;
        this.variantRepository = variantRepository;
    }

    public List<Allocation> optimizePickSequence(List<Allocation> allocations, Map<UUID, String> locationPaths) {
        return optimizePickSequence(allocations, locationPaths, Map.of());
    }

    /**
     * Non-overlapping walk: sort stops by hierarchical path segments
     * ({@code WH/ZONE/AISLE/BAY/SHELF}), then {@code sequence_index}, then path string.
     */
    public List<Allocation> optimizePickSequence(List<Allocation> allocations,
                                                 Map<UUID, String> locationPaths,
                                                 Map<UUID, Integer> sequenceIndexes) {
        if (allocations.size() <= 1) {
            return List.copyOf(allocations);
        }
        // Keep graph warm for wayfinding even though routing is hierarchical.
        try {
            UUID tenantId = TenantContext.requireTenantId();
            Map<UUID, Location> locations = indexLocations(tenantId);
            ensureWalkableGraph(tenantId, locations);
        } catch (RuntimeException ignored) {
            // Unit tests may construct without repos / tenant context.
        }

        Map<UUID, Boolean> fragileByVariant = fragileFlags(allocations);

        List<Allocation> route = new ArrayList<>(allocations);
        // Path first, then non-fragile before fragile so crushables land on top of the tote.
        route.sort(Comparator
                .comparing((Allocation a) -> Boolean.TRUE.equals(
                        fragileByVariant.get(a.getVariantId())))
                .thenComparing(a -> LocationPathKey.parse(
                        locationPaths.getOrDefault(a.getLocationId(), "")))
                .thenComparingInt(a -> sequenceIndexes.getOrDefault(a.getLocationId(), Integer.MAX_VALUE))
                .thenComparing(a -> locationPaths.getOrDefault(a.getLocationId(), "")));
        return List.copyOf(route);
    }

    private Map<UUID, Boolean> fragileFlags(List<Allocation> allocations) {
        Map<UUID, Boolean> flags = new HashMap<>();
        Set<UUID> variantIds = allocations.stream()
                .map(Allocation::getVariantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (variantIds.isEmpty() || variantRepository == null) {
            return flags;
        }
        for (ProductVariant variant : variantRepository.findAllById(variantIds)) {
            flags.put(variant.getId(), variant.isFragile());
        }
        return flags;
    }

    /**
     * A* polyline between two locations for scanner wayfinding.
     */
    @Transactional
    public WayfindingPath wayfinding(UUID fromLocationId, UUID toLocationId) {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, Location> locations = indexLocations(tenantId);
        ensureWalkableGraph(tenantId, locations);
        SpatialGraph graph = SpatialGraph.build(locations, walkableEdgeRepository.findByTenantId(tenantId));
        List<UUID> nodeIds = graph.shortestPathNodes(fromLocationId, toLocationId);
        List<Point> points = new ArrayList<>();
        for (UUID id : nodeIds) {
            Location loc = locations.get(id);
            if (loc == null) {
                continue;
            }
            points.add(Point.from(loc));
        }
        if (points.isEmpty()) {
            Location from = locations.get(fromLocationId);
            Location to = locations.get(toLocationId);
            if (from != null) {
                points.add(Point.from(from));
            }
            if (to != null) {
                points.add(Point.from(to));
            }
        }
        double cost = graph.shortestPathCost(fromLocationId, toLocationId);
        return new WayfindingPath(fromLocationId, toLocationId, cost, points);
    }

    /**
     * Re-sequence pending pick paths by hierarchical location segments, then sequence_index.
     */
    public List<String> optimizePendingPaths(List<String> pendingPaths, Map<String, Integer> pathToSequenceIndex) {
        if (pendingPaths.size() <= 1) {
            return List.copyOf(pendingPaths);
        }
        List<String> route = new ArrayList<>(pendingPaths);
        route.sort(Comparator
                .comparing(LocationPathKey::parse)
                .thenComparingInt(p -> pathToSequenceIndex.getOrDefault(p, Integer.MAX_VALUE))
                .thenComparing(p -> p));
        return List.copyOf(route);
    }

    /**
     * Parses paths like {@code WH-01/Z-A/A-1/B-01/S-02} into comparable hierarchical keys.
     */
    public static final class LocationPathKey implements Comparable<LocationPathKey> {
        private final List<Segment> segments;

        private LocationPathKey(List<Segment> segments) {
            this.segments = segments;
        }

        public static LocationPathKey parse(String path) {
            if (path == null || path.isBlank()) {
                return new LocationPathKey(List.of());
            }
            String[] parts = path.split("/");
            List<Segment> segs = new ArrayList<>(parts.length);
            for (String part : parts) {
                segs.add(Segment.parse(part.trim()));
            }
            return new LocationPathKey(List.copyOf(segs));
        }

        @Override
        public int compareTo(LocationPathKey other) {
            int n = Math.min(segments.size(), other.segments.size());
            for (int i = 0; i < n; i++) {
                int cmp = segments.get(i).compareTo(other.segments.get(i));
                if (cmp != 0) {
                    return cmp;
                }
            }
            return Integer.compare(segments.size(), other.segments.size());
        }

        private record Segment(String prefix, long number, String raw) implements Comparable<Segment> {
            static Segment parse(String token) {
                if (token == null || token.isEmpty()) {
                    return new Segment("", Long.MAX_VALUE, "");
                }
                int i = 0;
                while (i < token.length() && !Character.isDigit(token.charAt(i))) {
                    i++;
                }
                String prefix = token.substring(0, i).toUpperCase();
                long number = Long.MAX_VALUE;
                if (i < token.length()) {
                    int j = i;
                    while (j < token.length() && Character.isDigit(token.charAt(j))) {
                        j++;
                    }
                    try {
                        number = Long.parseLong(token.substring(i, j));
                    } catch (NumberFormatException ignored) {
                        number = Long.MAX_VALUE;
                    }
                }
                return new Segment(prefix, number, token.toUpperCase());
            }

            @Override
            public int compareTo(Segment o) {
                int cmp = prefix.compareTo(o.prefix);
                if (cmp != 0) {
                    return cmp;
                }
                cmp = Long.compare(number, o.number);
                if (cmp != 0) {
                    return cmp;
                }
                return raw.compareTo(o.raw);
            }
        }
    }

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

    private Map<UUID, Location> indexLocations(UUID tenantId) {
        Map<UUID, Location> map = new HashMap<>();
        for (Location loc : locationRepository.findByTenantIdOrderByPathAsc(tenantId)) {
            map.put(loc.getId(), loc);
        }
        return map;
    }

    /**
     * When no edges exist, connect BINs that share a parent (aisle) in sequence order,
     * and connect aisle endpoints to neighboring aisles under the same zone.
     */
    void ensureWalkableGraph(UUID tenantId, Map<UUID, Location> locations) {
        if (walkableEdgeRepository.countByTenantId(tenantId) > 0) {
            return;
        }
        Map<UUID, List<Location>> byParent = new HashMap<>();
        for (Location loc : locations.values()) {
            if (!"BIN".equalsIgnoreCase(loc.getType()) && !"AISLE".equalsIgnoreCase(loc.getType())) {
                continue;
            }
            if (loc.getParentLocationId() == null) {
                continue;
            }
            byParent.computeIfAbsent(loc.getParentLocationId(), k -> new ArrayList<>()).add(loc);
        }
        List<WalkableEdge> created = new ArrayList<>();
        for (List<Location> siblings : byParent.values()) {
            siblings.sort(Comparator
                    .comparingInt(Location::getSequenceIndex)
                    .thenComparing(Location::getPath));
            for (int i = 0; i < siblings.size() - 1; i++) {
                Location a = siblings.get(i);
                Location b = siblings.get(i + 1);
                created.add(edge(tenantId, a, b));
            }
        }
        // Cross-link leaf BINs under adjacent aisles (first↔first, last↔last)
        Map<UUID, List<Location>> aislesByZone = new HashMap<>();
        for (Location loc : locations.values()) {
            if ("AISLE".equalsIgnoreCase(loc.getType()) && loc.getParentLocationId() != null) {
                aislesByZone.computeIfAbsent(loc.getParentLocationId(), k -> new ArrayList<>()).add(loc);
            }
        }
        for (List<Location> aisles : aislesByZone.values()) {
            aisles.sort(Comparator.comparingInt(Location::getSequenceIndex).thenComparing(Location::getPath));
            for (int i = 0; i < aisles.size() - 1; i++) {
                List<Location> binsA = byParent.getOrDefault(aisles.get(i).getId(), List.of()).stream()
                        .filter(l -> "BIN".equalsIgnoreCase(l.getType()))
                        .sorted(Comparator.comparingInt(Location::getSequenceIndex))
                        .toList();
                List<Location> binsB = byParent.getOrDefault(aisles.get(i + 1).getId(), List.of()).stream()
                        .filter(l -> "BIN".equalsIgnoreCase(l.getType()))
                        .sorted(Comparator.comparingInt(Location::getSequenceIndex))
                        .toList();
                if (!binsA.isEmpty() && !binsB.isEmpty()) {
                    created.add(edge(tenantId, binsA.getFirst(), binsB.getFirst()));
                    if (binsA.size() > 1 || binsB.size() > 1) {
                        created.add(edge(tenantId, binsA.getLast(), binsB.getLast()));
                    }
                }
            }
        }
        if (!created.isEmpty()) {
            walkableEdgeRepository.saveAll(created);
        }
    }

    private static WalkableEdge edge(UUID tenantId, Location a, Location b) {
        WalkableEdge edge = new WalkableEdge();
        edge.setTenantId(tenantId);
        edge.setNodeAId(a.getId());
        edge.setNodeBId(b.getId());
        edge.setDistance(BigDecimal.valueOf(Math.max(0.1, Point.from(a).distanceTo(Point.from(b)))));
        return edge;
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

    public record WayfindingPath(
            UUID fromLocationId,
            UUID toLocationId,
            double travelCost,
            List<Point> points
    ) {
    }

    public record Point(double x, double y, UUID locationId, String code) {
        public static Point from(Location loc) {
            double x = loc.getCoordX() != null ? loc.getCoordX().doubleValue()
                    : (loc.getSequenceIndex() % 20) * 10.0;
            double y = loc.getCoordY() != null ? loc.getCoordY().doubleValue()
                    : (loc.getSequenceIndex() / 20.0) * 10.0;
            return new Point(x, y, loc.getId(), loc.getCode());
        }

        public double distanceTo(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            return Math.hypot(dx, dy);
        }
    }

    private record PathNode(int index, String path, PathCoordinate coord) {
    }

    private record PathCoordinate(int warehouse, int zone, int aisle, int bin, int sequenceIndex) {
        static PathCoordinate fromPath(String path, int sequenceIndex) {
            String[] parts = path == null ? new String[0] : path.split("/");
            return new PathCoordinate(hash(parts, 0), hash(parts, 1), hash(parts, 2), hash(parts, 3), sequenceIndex);
        }

        private static int hash(String[] parts, int index) {
            if (index >= parts.length) {
                return 0;
            }
            return Math.abs(parts[index].hashCode() % 10_000);
        }

        double distanceTo(PathCoordinate other) {
            double seqDelta = Math.abs(sequenceIndex - other.sequenceIndex);
            return seqDelta * 50
                    + Math.abs(warehouse - other.warehouse) * 1000
                    + Math.abs(zone - other.zone) * 100
                    + Math.abs(aisle - other.aisle) * 10
                    + Math.abs(bin - other.bin);
        }
    }

    /**
     * Undirected weighted graph with A* shortest paths.
     */
    static final class SpatialGraph {
        private final Map<UUID, Point> points;
        private final Map<UUID, List<Neighbor>> adjacency;
        private final Map<String, Double> cache = new HashMap<>();
        private final Map<String, List<UUID>> pathCache = new HashMap<>();

        private SpatialGraph(Map<UUID, Point> points, Map<UUID, List<Neighbor>> adjacency) {
            this.points = points;
            this.adjacency = adjacency;
        }

        static SpatialGraph build(Map<UUID, Location> locations, List<WalkableEdge> edges) {
            Map<UUID, Point> points = new HashMap<>();
            for (Location loc : locations.values()) {
                points.put(loc.getId(), Point.from(loc));
            }
            Map<UUID, List<Neighbor>> adjacency = new HashMap<>();
            for (WalkableEdge edge : edges) {
                double dist = edge.getDistance().doubleValue();
                adjacency.computeIfAbsent(edge.getNodeAId(), k -> new ArrayList<>())
                        .add(new Neighbor(edge.getNodeBId(), dist));
                adjacency.computeIfAbsent(edge.getNodeBId(), k -> new ArrayList<>())
                        .add(new Neighbor(edge.getNodeAId(), dist));
            }
            // Euclidean fallback edges for any BIN without graph neighbors
            List<UUID> bins = locations.values().stream()
                    .filter(l -> "BIN".equalsIgnoreCase(l.getType()))
                    .map(Location::getId)
                    .toList();
            for (UUID id : bins) {
                if (adjacency.containsKey(id) && !adjacency.get(id).isEmpty()) {
                    continue;
                }
                Point p = points.get(id);
                List<Neighbor> neighbors = new ArrayList<>();
                for (UUID otherId : bins) {
                    if (Objects.equals(id, otherId)) {
                        continue;
                    }
                    Point q = points.get(otherId);
                    if (p != null && q != null) {
                        neighbors.add(new Neighbor(otherId, p.distanceTo(q)));
                    }
                }
                neighbors.sort(Comparator.comparingDouble(Neighbor::distance));
                adjacency.put(id, neighbors.stream().limit(6).toList());
            }
            return new SpatialGraph(points, adjacency);
        }

        double shortestPathCost(UUID from, UUID to) {
            if (from == null || to == null) {
                return Double.MAX_VALUE / 4;
            }
            if (from.equals(to)) {
                return 0;
            }
            String key = from + ">" + to;
            Double cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            AStarResult result = aStar(from, to);
            cache.put(key, result.cost());
            pathCache.put(key, result.nodes());
            return result.cost();
        }

        List<UUID> shortestPathNodes(UUID from, UUID to) {
            if (from == null || to == null) {
                return List.of();
            }
            if (from.equals(to)) {
                return List.of(from);
            }
            String key = from + ">" + to;
            List<UUID> cached = pathCache.get(key);
            if (cached != null) {
                return cached;
            }
            AStarResult result = aStar(from, to);
            cache.put(key, result.cost());
            pathCache.put(key, result.nodes());
            return result.nodes();
        }

        private AStarResult aStar(UUID start, UUID goal) {
            Point goalPoint = points.get(goal);
            PriorityQueue<AStarNode> open = new PriorityQueue<>(Comparator.comparingDouble(AStarNode::fScore));
            Map<UUID, Double> gScore = new HashMap<>();
            Map<UUID, UUID> cameFrom = new HashMap<>();
            Set<UUID> closed = new HashSet<>();

            gScore.put(start, 0.0);
            open.add(new AStarNode(start, heuristic(start, goalPoint)));

            while (!open.isEmpty()) {
                AStarNode current = open.poll();
                if (closed.contains(current.id())) {
                    continue;
                }
                if (current.id().equals(goal)) {
                    return new AStarResult(gScore.getOrDefault(goal, Double.MAX_VALUE / 4), reconstruct(cameFrom, goal));
                }
                closed.add(current.id());
                for (Neighbor neighbor : adjacency.getOrDefault(current.id(), List.of())) {
                    if (closed.contains(neighbor.id())) {
                        continue;
                    }
                    double tentative = gScore.getOrDefault(current.id(), Double.MAX_VALUE / 4) + neighbor.distance();
                    if (tentative < gScore.getOrDefault(neighbor.id(), Double.MAX_VALUE / 4)) {
                        cameFrom.put(neighbor.id(), current.id());
                        gScore.put(neighbor.id(), tentative);
                        open.add(new AStarNode(neighbor.id(), tentative + heuristic(neighbor.id(), goalPoint)));
                    }
                }
            }

            // Disconnected: Euclidean fallback
            Point a = points.get(start);
            Point b = points.get(goal);
            double euclid = (a != null && b != null) ? a.distanceTo(b) : 1_000_000;
            return new AStarResult(euclid, List.of(start, goal));
        }

        private double heuristic(UUID nodeId, Point goal) {
            Point p = points.get(nodeId);
            if (p == null || goal == null) {
                return 0;
            }
            return p.distanceTo(goal);
        }

        private static List<UUID> reconstruct(Map<UUID, UUID> cameFrom, UUID goal) {
            List<UUID> path = new ArrayList<>();
            UUID cur = goal;
            path.add(cur);
            while (cameFrom.containsKey(cur)) {
                cur = cameFrom.get(cur);
                path.addFirst(cur);
            }
            return path;
        }

        private record Neighbor(UUID id, double distance) {
        }

        private record AStarNode(UUID id, double fScore) {
        }

        private record AStarResult(double cost, List<UUID> nodes) {
        }
    }
}
