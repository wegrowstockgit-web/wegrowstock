package com.invsys.rtls;

import com.invsys.common.ApiException;
import com.invsys.domain.Location;
import com.invsys.domain.RtlsPositionEvent;
import com.invsys.domain.RtlsTag;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.RtlsPositionEventRepository;
import com.invsys.repository.RtlsTagRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Translates BLE AoA / UWB telemetry packets into bound asset coordinates.
 */
@Service
public class RtlsTelemetryService {

    private final RtlsTagRepository tagRepository;
    private final RtlsPositionEventRepository positionRepository;
    private final LocationRepository locationRepository;
    private final RtlsSseHub sseHub;

    public RtlsTelemetryService(RtlsTagRepository tagRepository,
                                RtlsPositionEventRepository positionRepository,
                                LocationRepository locationRepository,
                                RtlsSseHub sseHub) {
        this.tagRepository = tagRepository;
        this.positionRepository = positionRepository;
        this.locationRepository = locationRepository;
        this.sseHub = sseHub;
    }

    @Transactional
    public RtlsTag upsertTag(UpsertTagRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String tagId = requireTagId(request.tagId());
        String technology = normalizeTechnology(request.technology());
        RtlsTag tag = tagRepository.findByTenantIdAndTagId(tenantId, tagId).orElseGet(() -> {
            RtlsTag created = new RtlsTag();
            created.setTenantId(tenantId);
            created.setTagId(tagId);
            return created;
        });
        tag.setTechnology(technology);
        tag.setAssetType(normalizeAssetType(request.assetType()));
        tag.setAssetRef(request.assetRef());
        tag.setLabel(request.label());
        tag.setActive(request.active() == null || request.active());
        return tagRepository.save(tag);
    }

    @Transactional(readOnly = true)
    public List<RtlsTag> listTags() {
        return tagRepository.findByTenantIdAndActiveTrueOrderByTagIdAsc(TenantContext.requireTenantId());
    }

    @Transactional
    public List<PositionFrame> ingest(List<TelemetryPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "packets required");
        }
        UUID tenantId = TenantContext.requireTenantId();
        List<PositionFrame> frames = new ArrayList<>(packets.size());
        for (TelemetryPacket packet : packets) {
            frames.add(ingestOne(tenantId, packet));
        }
        return frames;
    }

    @Transactional(readOnly = true)
    public List<PositionFrame> recent() {
        return positionRepository.findTop100ByTenantIdOrderByObservedAtDesc(TenantContext.requireTenantId())
                .stream()
                .map(this::toFrame)
                .toList();
    }

    /**
     * Bind a BLE/UWB tag to a pallet / PO receive asset for directed putaway tracking.
     */
    @Transactional
    public RtlsTag bindPalletTag(String tagId, UUID assetRef, String label) {
        return upsertTag(new UpsertTagRequest(tagId, "BLE_AOA", "PALLET", assetRef, label, true));
    }

    /**
     * If any active tag is bound to {@code assetRef}, emit a position at the bin coordinates.
     */
    @Transactional
    public Optional<PositionFrame> announceAssetAtLocation(UUID assetRef, UUID locationId) {
        if (assetRef == null || locationId == null) {
            return Optional.empty();
        }
        UUID tenantId = TenantContext.requireTenantId();
        List<RtlsTag> tags = tagRepository.findByTenantIdAndAssetRefAndActiveTrue(tenantId, assetRef);
        if (tags.isEmpty()) {
            return Optional.empty();
        }
        Location location = locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElse(null);
        if (location == null) {
            return Optional.empty();
        }
        BigDecimal x = location.getCoordX() != null ? location.getCoordX() : BigDecimal.ZERO;
        BigDecimal y = location.getCoordY() != null ? location.getCoordY() : BigDecimal.ZERO;
        RtlsTag tag = tags.getFirst();
        PositionFrame frame = ingestOne(tenantId, new TelemetryPacket(
                tag.getTagId(),
                tag.getTechnology(),
                x,
                y,
                location.getCoordZ(),
                null,
                null,
                null,
                null,
                TenantContext.getWarehouseId().orElse(null),
                Instant.now(),
                Map.of("source", "PO_RECEIPT_PUTAWAY", "locationId", locationId.toString())));
        return Optional.of(frame);
    }

    private PositionFrame ingestOne(UUID tenantId, TelemetryPacket packet) {
        String tagId = requireTagId(packet.tagId());
        String technology = normalizeTechnology(packet.technology());
        BigDecimal x = packet.x() != null ? packet.x() : packet.azimuth() != null
                ? BigDecimal.valueOf(Math.cos(Math.toRadians(packet.azimuth().doubleValue()))
                * (packet.rangeM() != null ? packet.rangeM().doubleValue() : 1d))
                : null;
        BigDecimal y = packet.y() != null ? packet.y() : packet.azimuth() != null
                ? BigDecimal.valueOf(Math.sin(Math.toRadians(packet.azimuth().doubleValue()))
                * (packet.rangeM() != null ? packet.rangeM().doubleValue() : 1d))
                : null;
        if (x == null || y == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Packet requires x/y or BLE AoA azimuth+rangeM");
        }

        RtlsTag tag = tagRepository.findByTenantIdAndTagId(tenantId, tagId).orElseGet(() -> {
            RtlsTag created = new RtlsTag();
            created.setTenantId(tenantId);
            created.setTagId(tagId);
            created.setTechnology(technology);
            created.setAssetType("UNKNOWN");
            return tagRepository.save(created);
        });

        RtlsPositionEvent event = new RtlsPositionEvent();
        event.setTenantId(tenantId);
        event.setTagId(tagId);
        event.setTechnology(technology);
        event.setX(x);
        event.setY(y);
        event.setZ(packet.z());
        event.setAccuracyM(packet.accuracyM());
        event.setHeadingDeg(packet.headingDeg());
        event.setAssetType(tag.getAssetType());
        event.setAssetRef(tag.getAssetRef());
        event.setWarehouseId(packet.warehouseId());
        event.setObservedAt(packet.observedAt() != null ? packet.observedAt() : Instant.now());
        Map<String, Object> raw = new LinkedHashMap<>();
        if (packet.raw() != null) {
            raw.putAll(packet.raw());
        }
        raw.put("technology", technology);
        event.setRawPayload(raw);
        event = positionRepository.save(event);

        PositionFrame frame = toFrame(event);
        sseHub.publish(tenantId, frame.toMap());
        return frame;
    }

    private PositionFrame toFrame(RtlsPositionEvent event) {
        return new PositionFrame(
                event.getId(),
                event.getTagId(),
                event.getTechnology(),
                event.getX(),
                event.getY(),
                event.getZ(),
                event.getAssetType(),
                event.getAssetRef(),
                event.getWarehouseId(),
                event.getObservedAt());
    }

    private static String requireTagId(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "tagId is required");
        }
        return tagId.trim();
    }

    private static String normalizeTechnology(String raw) {
        if (raw == null || raw.isBlank()) {
            return "OTHER";
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (key) {
            case "BLE", "BLE_AOA", "AOA" -> "BLE_AOA";
            case "UWB", "ULTRA_WIDEBAND" -> "UWB";
            case "WIFI_RTT", "WIFI" -> "WIFI_RTT";
            default -> "OTHER";
        };
    }

    private static String normalizeAssetType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "UNKNOWN";
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "USER", "PALLET", "VEHICLE", "TOTE", "UNKNOWN" -> key;
            default -> "UNKNOWN";
        };
    }

    public record UpsertTagRequest(
            String tagId,
            String technology,
            String assetType,
            UUID assetRef,
            String label,
            Boolean active
    ) {
    }

    public record TelemetryPacket(
            String tagId,
            String technology,
            BigDecimal x,
            BigDecimal y,
            BigDecimal z,
            BigDecimal azimuth,
            BigDecimal rangeM,
            BigDecimal accuracyM,
            BigDecimal headingDeg,
            UUID warehouseId,
            Instant observedAt,
            Map<String, Object> raw
    ) {
    }

    public record PositionFrame(
            UUID id,
            String tagId,
            String technology,
            BigDecimal x,
            BigDecimal y,
            BigDecimal z,
            String assetType,
            UUID assetRef,
            UUID warehouseId,
            Instant observedAt
    ) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id.toString());
            map.put("tagId", tagId);
            map.put("technology", technology);
            map.put("x", x);
            map.put("y", y);
            map.put("z", z);
            map.put("assetType", assetType);
            map.put("assetRef", assetRef != null ? assetRef.toString() : null);
            map.put("warehouseId", warehouseId != null ? warehouseId.toString() : null);
            map.put("observedAt", observedAt.toString());
            return map;
        }
    }
}
