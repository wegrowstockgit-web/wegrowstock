package com.invsys.api;

import com.invsys.modules.fulfillment.domain.FulfillmentException;
import com.invsys.service.FulfillmentExceptionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/office/exceptions")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class OfficeExceptionController {

    private final FulfillmentExceptionService exceptionService;

    public OfficeExceptionController(FulfillmentExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    @GetMapping("/list")
    public List<ExceptionResponse> list(@RequestParam(required = false) String status) {
        return exceptionService.list(status).stream().map(this::toResponse).toList();
    }

    @PostMapping("/{id}/resolve")
    public ExceptionResponse resolve(@PathVariable UUID id, @RequestBody ResolveBody body) {
        FulfillmentException resolved = exceptionService.resolve(
                id,
                new FulfillmentExceptionService.ResolveRequest(
                        body != null ? body.action() : "CLEAR",
                        body != null ? body.lotNumber() : null,
                        body != null ? body.notes() : null));
        return toResponse(resolved);
    }

    private ExceptionResponse toResponse(FulfillmentException e) {
        return new ExceptionResponse(
                e.getId(),
                e.getAllocationId(),
                e.getReportedBy(),
                e.getWarehouseId(),
                e.getResolutionStatus(),
                e.getMetadata(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    public record ResolveBody(String action, String lotNumber, String notes) {
    }

    public record ExceptionResponse(
            UUID id,
            UUID allocationId,
            UUID reportedBy,
            UUID warehouseId,
            String resolutionStatus,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
