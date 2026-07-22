package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.WalkableEdge;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.WalkableEdgeRepository;
import com.invsys.core.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.invsys.modules.fulfillment.service.PickingService;

@Service
public class SpatialMapService {

    private final LocationRepository locationRepository;
    private final WalkableEdgeRepository walkableEdgeRepository;
    private final DSLContext dsl;

    public SpatialMapService(LocationRepository locationRepository,
                             WalkableEdgeRepository walkableEdgeRepository,
                             DSLContext dsl) {
        this.locationRepository = locationRepository;
        this.walkableEdgeRepository = walkableEdgeRepository;
        this.dsl = dsl;
    }

    @Transactional
    public Location updateCoordinates(UUID locationId, BigDecimal coordX, BigDecimal coordY, BigDecimal coordZ) {
        UUID tenantId = TenantContext.requireTenantId();
        Location location = locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Location not found"));
        location.setCoordX(coordX);
        location.setCoordY(coordY);
        if (coordZ != null) {
            location.setCoordZ(coordZ);
        }
        return locationRepository.save(location);
    }

    @Transactional(readOnly = true)
    public List<HeatmapCell> heatmap(int days) {
        UUID tenantId = TenantContext.requireTenantId();
        int window = Math.max(1, Math.min(days, 90));
        Result<Record> rows = dsl.fetch("""
                SELECT l.id AS location_id,
                       l.code AS code,
                       l.path AS path,
                       l.coord_x AS coord_x,
                       l.coord_y AS coord_y,
                       COUNT(il.id)::bigint AS movement_count
                FROM locations l
                LEFT JOIN inventory_ledger il
                       ON il.location_id = l.id
                      AND il.tenant_id = l.tenant_id
                      AND il.created_at >= NOW() - (? || ' days')::interval
                WHERE l.tenant_id = ?
                GROUP BY l.id, l.code, l.path, l.coord_x, l.coord_y
                """, String.valueOf(window), tenantId);

        long max = 0;
        List<HeatmapCell> cells = new ArrayList<>();
        for (Record row : rows) {
            long count = row.get("movement_count", Long.class);
            max = Math.max(max, count);
            cells.add(new HeatmapCell(
                    row.get("location_id", UUID.class),
                    row.get("code", String.class),
                    row.get("path", String.class),
                    row.get("coord_x", BigDecimal.class),
                    row.get("coord_y", BigDecimal.class),
                    count,
                    0));
        }
        final long peak = Math.max(max, 1);
        return cells.stream()
                .map(c -> new HeatmapCell(c.locationId(), c.code(), c.path(), c.coordX(), c.coordY(),
                        c.movementCount(), c.movementCount() / (double) peak))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WalkableEdge> listEdges() {
        return walkableEdgeRepository.findByTenantId(TenantContext.requireTenantId());
    }

    @Transactional
    public WalkableEdge createEdge(UUID nodeAId, UUID nodeBId, BigDecimal distance) {
        UUID tenantId = TenantContext.requireTenantId();
        requireLocation(tenantId, nodeAId);
        requireLocation(tenantId, nodeBId);
        WalkableEdge edge = new WalkableEdge();
        edge.setTenantId(tenantId);
        edge.setNodeAId(nodeAId);
        edge.setNodeBId(nodeBId);
        if (distance != null && distance.signum() > 0) {
            edge.setDistance(distance);
        } else {
            Location a = locationRepository.findById(nodeAId).orElseThrow();
            Location b = locationRepository.findById(nodeBId).orElseThrow();
            PickingService.Point pa = PickingService.Point.from(a);
            PickingService.Point pb = PickingService.Point.from(b);
            edge.setDistance(BigDecimal.valueOf(Math.max(0.1, pa.distanceTo(pb))));
        }
        return walkableEdgeRepository.save(edge);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Location> locationsById() {
        Map<UUID, Location> map = new HashMap<>();
        for (Location loc : locationRepository.findByTenantIdOrderByPathAsc(TenantContext.requireTenantId())) {
            map.put(loc.getId(), loc);
        }
        return map;
    }

    private void requireLocation(UUID tenantId, UUID locationId) {
        locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND",
                        "Location not found: " + locationId));
    }

    public record HeatmapCell(
            UUID locationId,
            String code,
            String path,
            BigDecimal coordX,
            BigDecimal coordY,
            long movementCount,
            double intensity
    ) {
    }
}
