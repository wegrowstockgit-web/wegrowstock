package com.invsys.api;

import com.invsys.domain.RtlsTag;
import com.invsys.rtls.RtlsSseHub;
import com.invsys.rtls.RtlsTelemetryService;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rtls")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class RtlsController {

    private final RtlsTelemetryService telemetryService;
    private final RtlsSseHub sseHub;

    public RtlsController(RtlsTelemetryService telemetryService, RtlsSseHub sseHub) {
        this.telemetryService = telemetryService;
        this.sseHub = sseHub;
    }

    @GetMapping("/tags")
    public List<TagResponse> listTags() {
        return telemetryService.listTags().stream().map(this::toTag).toList();
    }

    @PutMapping("/tags")
    public TagResponse upsertTag(@Valid @RequestBody UpsertTagBody body) {
        return toTag(telemetryService.upsertTag(new RtlsTelemetryService.UpsertTagRequest(
                body.tagId(), body.technology(), body.assetType(), body.assetRef(), body.label(), body.active())));
    }

    @PostMapping("/telemetry")
    public List<RtlsTelemetryService.PositionFrame> ingest(@Valid @RequestBody TelemetryBatch body) {
        return telemetryService.ingest(body.packets().stream()
                .map(p -> new RtlsTelemetryService.TelemetryPacket(
                        p.tagId(), p.technology(), p.x(), p.y(), p.z(),
                        p.azimuth(), p.rangeM(), p.accuracyM(), p.headingDeg(),
                        p.warehouseId(), p.observedAt(), p.raw()))
                .toList());
    }

    @GetMapping("/positions/recent")
    public List<RtlsTelemetryService.PositionFrame> recent() {
        return telemetryService.recent();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseHub.subscribe(TenantContext.requireTenantId());
    }

    private TagResponse toTag(RtlsTag tag) {
        return new TagResponse(tag.getId(), tag.getTagId(), tag.getTechnology(),
                tag.getAssetType(), tag.getAssetRef(), tag.getLabel(), tag.isActive());
    }

    public record UpsertTagBody(
            @NotBlank String tagId,
            String technology,
            String assetType,
            UUID assetRef,
            String label,
            Boolean active
    ) {
    }

    public record TelemetryBatch(@NotEmpty List<PacketBody> packets) {
    }

    public record PacketBody(
            @NotBlank String tagId,
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

    public record TagResponse(
            UUID id,
            String tagId,
            String technology,
            String assetType,
            UUID assetRef,
            String label,
            boolean active
    ) {
    }
}
