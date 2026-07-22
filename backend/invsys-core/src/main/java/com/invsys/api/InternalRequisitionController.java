package com.invsys.api;

import com.invsys.api.dto.InternalRequisitionResponse;
import com.invsys.service.InternalConsumptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal-requisitions")
public class InternalRequisitionController {

    private final InternalConsumptionService consumptionService;

    public InternalRequisitionController(InternalConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public List<InternalRequisitionResponse> list(@RequestParam(required = false) String status) {
        return consumptionService.listRequisitions(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public InternalRequisitionResponse get(@PathVariable UUID id) {
        return consumptionService.getRequisition(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public InternalRequisitionResponse create(@Valid @RequestBody CreateRequisitionRequest request) {
        List<InternalConsumptionService.RequisitionLineInput> lines = request.lines().stream()
                .map(l -> new InternalConsumptionService.RequisitionLineInput(l.variantId(), l.qtyRequested()))
                .toList();
        return consumptionService.createRequisition(request.costCenterId(), lines);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public InternalRequisitionResponse approve(@PathVariable UUID id) {
        return consumptionService.approveRequisition(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public InternalRequisitionResponse cancel(@PathVariable UUID id) {
        return consumptionService.cancelRequisition(id);
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public InternalRequisitionResponse issue(@PathVariable UUID id, @Valid @RequestBody IssueRequest request) {
        return consumptionService.issueRequisition(id, request.locationId());
    }

    public record CreateRequisitionRequest(
            @NotNull UUID costCenterId,
            @NotEmpty List<LineRequest> lines
    ) {
    }

    public record LineRequest(
            @NotNull UUID variantId,
            @NotNull @Positive BigDecimal qtyRequested
    ) {
    }

    public record IssueRequest(@NotNull UUID locationId) {
    }
}
