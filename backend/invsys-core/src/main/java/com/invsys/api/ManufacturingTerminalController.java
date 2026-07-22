package com.invsys.api;

import com.invsys.api.dto.ManufacturingOperationResponse;
import com.invsys.api.dto.ProductionTimesheetResponse;
import com.invsys.domain.ManufacturingOperation;
import com.invsys.service.ManufacturingLaborService;
import com.invsys.service.ManufacturingService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manufacturing")
public class ManufacturingTerminalController {

    private final ManufacturingLaborService laborService;
    private final ManufacturingService manufacturingService;

    public ManufacturingTerminalController(ManufacturingLaborService laborService,
                                           ManufacturingService manufacturingService) {
        this.laborService = laborService;
        this.manufacturingService = manufacturingService;
    }

    @GetMapping("/orders/{id}/operations")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public List<ManufacturingOperationResponse> listOperations(@PathVariable UUID id) {
        return laborService.listOperationsForOrder(id).stream()
                .map(op -> new ManufacturingOperationResponse(op.getId(), op.getName(), op.getDefaultHourlyRate()))
                .toList();
    }

    @GetMapping("/orders/{id}/timesheets")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public List<ProductionTimesheetResponse> listTimesheets(@PathVariable UUID id) {
        return laborService.listTimesheets(id);
    }

    @PostMapping("/orders/{id}/timesheets/start")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public ProductionTimesheetResponse startTimesheet(@PathVariable UUID id,
                                                      @Valid @RequestBody StartTimesheetRequest request) {
        return laborService.toResponse(laborService.startTimesheet(id, request.operationId()));
    }

    @PostMapping("/timesheets/{id}/stop")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public ProductionTimesheetResponse stopTimesheet(@PathVariable UUID id) {
        return laborService.toResponse(laborService.stopTimesheet(id));
    }

    @PostMapping("/disassemble")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public void disassemble(@Valid @RequestBody DisassembleRequest request) {
        manufacturingService.disassemble(request.variantId(), request.locationId(), request.quantity());
    }

    public record StartTimesheetRequest(@NotNull UUID operationId) {
    }

    public record DisassembleRequest(
            @NotNull UUID variantId,
            @NotNull UUID locationId,
            @NotNull @Positive BigDecimal quantity
    ) {
    }
}
