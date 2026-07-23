package com.invsys.api;

import com.invsys.core.security.PermissionKeys;
import com.invsys.core.security.RequirePermission;
import com.invsys.domain.RmaQcInspection;
import com.invsys.service.RmaQualityControlService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/returns/qc")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class RmaQcController {

    private final RmaQualityControlService qualityControlService;

    public RmaQcController(RmaQualityControlService qualityControlService) {
        this.qualityControlService = qualityControlService;
    }

    @PostMapping("/inspections")
    @RequirePermission(PermissionKeys.RETURNS_QC_PROCESS)
    public InspectionResponse processInspection(@Valid @RequestBody InspectionRequestBody body) {
        RmaQcInspection inspection = qualityControlService.processInspection(
                new RmaQualityControlService.InspectionRequest(
                        body.returnLineId(),
                        body.grade(),
                        body.dispositionAction(),
                        body.inspectionNotes(),
                        body.photoAttachmentIds(),
                        body.targetLocationId(),
                        body.quantity()));
        return toResponse(inspection);
    }

    private InspectionResponse toResponse(RmaQcInspection inspection) {
        return new InspectionResponse(
                inspection.getId(),
                inspection.getReturnLineId(),
                inspection.getInspectorUserId(),
                inspection.getGrade(),
                inspection.getInspectionNotes(),
                inspection.getPhotoAttachmentIds(),
                inspection.getDispositionAction(),
                inspection.getCreatedAt());
    }

    public record InspectionRequestBody(
            @NotNull UUID returnLineId,
            @NotBlank String grade,
            @NotBlank String dispositionAction,
            String inspectionNotes,
            List<UUID> photoAttachmentIds,
            UUID targetLocationId,
            BigDecimal quantity
    ) {
    }

    public record InspectionResponse(
            UUID id,
            UUID returnLineId,
            UUID inspectorUserId,
            String grade,
            String inspectionNotes,
            List<UUID> photoAttachmentIds,
            String dispositionAction,
            Instant createdAt
    ) {
    }
}
