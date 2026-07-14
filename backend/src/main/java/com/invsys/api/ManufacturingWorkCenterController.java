package com.invsys.api;

import com.invsys.common.ApiException;
import com.invsys.domain.ManufacturingWorkCenter;
import com.invsys.repository.ManufacturingWorkCenterRepository;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manufacturing/work-centers")
public class ManufacturingWorkCenterController {

    private final ManufacturingWorkCenterRepository workCenterRepository;

    public ManufacturingWorkCenterController(ManufacturingWorkCenterRepository workCenterRepository) {
        this.workCenterRepository = workCenterRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public List<WorkCenterResponse> list() {
        UUID tenantId = TenantContext.requireTenantId();
        return workCenterRepository.findByTenantIdOrderByCodeAsc(tenantId).stream()
                .map(WorkCenterResponse::from)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public WorkCenterResponse create(@Valid @RequestBody CreateWorkCenterRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        workCenterRepository.findByTenantIdAndCode(tenantId, request.code().trim()).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "WORK_CENTER_EXISTS", "Work center code already exists");
        });
        ManufacturingWorkCenter center = new ManufacturingWorkCenter();
        center.setTenantId(tenantId);
        center.setCode(request.code().trim());
        center.setName(request.name().trim());
        String status = request.status() != null && !request.status().isBlank()
                ? request.status()
                : request.operationalStatus();
        center.setOperationalStatus(status != null && !status.isBlank()
                ? status.trim().toUpperCase()
                : "ACTIVE");
        center.setLocationId(request.locationId());
        if (request.capacity() != null) {
            center.setCapacity(request.capacity());
        }
        return WorkCenterResponse.from(workCenterRepository.save(center));
    }

    public record CreateWorkCenterRequest(
            @NotBlank String code,
            @NotBlank String name,
            String operationalStatus,
            String status,
            UUID locationId,
            BigDecimal capacity
    ) {
    }

    public record WorkCenterResponse(
            UUID id,
            String code,
            String name,
            String operationalStatus,
            String status,
            UUID locationId,
            BigDecimal capacity
    ) {
        static WorkCenterResponse from(ManufacturingWorkCenter center) {
            return new WorkCenterResponse(
                    center.getId(),
                    center.getCode(),
                    center.getName(),
                    center.getOperationalStatus(),
                    center.getStatus(),
                    center.getLocationId(),
                    center.getCapacity());
        }
    }
}
