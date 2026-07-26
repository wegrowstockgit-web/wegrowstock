package com.invsys.api;

import com.invsys.domain.DockAppointment;
import com.invsys.service.DockSchedulingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dock-appointments")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class DockSchedulingController {

    private final DockSchedulingService dockSchedulingService;

    public DockSchedulingController(DockSchedulingService dockSchedulingService) {
        this.dockSchedulingService = dockSchedulingService;
    }

    @PostMapping
    public AppointmentResponse schedule(@Valid @RequestBody ScheduleBody body) {
        DockAppointment appointment = dockSchedulingService.scheduleAppointment(
                new DockSchedulingService.ScheduleRequest(
                        body.warehouseId(),
                        body.dockDoorNumber(),
                        body.purchaseOrderId(),
                        body.carrierName(),
                        body.driverName(),
                        body.truckLicensePlate(),
                        body.appointmentStart(),
                        body.appointmentEnd()));
        return toResponse(appointment);
    }

    @GetMapping
    public List<AppointmentResponse> list(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return dockSchedulingService.listByWarehouse(warehouseId, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{id}/check-in")
    public AppointmentResponse checkIn(@PathVariable UUID id) {
        return toResponse(dockSchedulingService.checkInDriver(id));
    }

    @PutMapping("/{id}/status")
    public AppointmentResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusBody body) {
        return toResponse(dockSchedulingService.updateStatus(id, body.status()));
    }

    private AppointmentResponse toResponse(DockAppointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getWarehouseId(),
                appointment.getDockDoorNumber(),
                appointment.getPurchaseOrderId(),
                appointment.getCarrierName(),
                appointment.getDriverName(),
                appointment.getTruckLicensePlate(),
                appointment.getAppointmentStart(),
                appointment.getAppointmentEnd(),
                appointment.getStatus(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt());
    }

    public record ScheduleBody(
            @NotNull UUID warehouseId,
            int dockDoorNumber,
            UUID purchaseOrderId,
            String carrierName,
            String driverName,
            String truckLicensePlate,
            @NotNull Instant appointmentStart,
            @NotNull Instant appointmentEnd
    ) {
    }

    public record StatusBody(@NotNull String status) {
    }

    public record AppointmentResponse(
            UUID id,
            UUID warehouseId,
            int dockDoorNumber,
            UUID purchaseOrderId,
            String carrierName,
            String driverName,
            String truckLicensePlate,
            Instant appointmentStart,
            Instant appointmentEnd,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
