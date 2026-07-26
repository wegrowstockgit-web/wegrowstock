package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.DockAppointment;
import com.invsys.repository.DockAppointmentRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DockSchedulingService {

    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "NO_SHOW", "CANCELLED");

    private final DockAppointmentRepository appointmentRepository;

    public DockSchedulingService(DockAppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public DockAppointment scheduleAppointment(ScheduleRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        validateWindow(request.appointmentStart(), request.appointmentEnd());

        List<DockAppointment> conflicts = appointmentRepository.findOverlapping(
                tenantId,
                request.warehouseId(),
                request.dockDoorNumber(),
                request.appointmentStart(),
                request.appointmentEnd());
        if (!conflicts.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT",
                    "Dock door is already booked for the requested time window");
        }

        DockAppointment appointment = new DockAppointment();
        appointment.setTenantId(tenantId);
        appointment.setWarehouseId(request.warehouseId());
        appointment.setDockDoorNumber(request.dockDoorNumber());
        appointment.setPurchaseOrderId(request.purchaseOrderId());
        appointment.setCarrierName(request.carrierName());
        appointment.setDriverName(request.driverName());
        appointment.setTruckLicensePlate(request.truckLicensePlate());
        appointment.setAppointmentStart(request.appointmentStart());
        appointment.setAppointmentEnd(request.appointmentEnd());
        appointment.setStatus("SCHEDULED");
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public DockAppointment checkInDriver(UUID appointmentId) {
        DockAppointment appointment = requireAppointment(appointmentId);
        if (TERMINAL_STATUSES.contains(appointment.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Cannot check in a terminal appointment");
        }
        appointment.setStatus("CHECKED_IN");
        return appointmentRepository.save(appointment);
    }

    @Transactional(readOnly = true)
    public List<DockAppointment> listByWarehouse(UUID warehouseId, Instant from, Instant to) {
        UUID tenantId = TenantContext.requireTenantId();
        Instant rangeStart = from != null ? from : Instant.now().minusSeconds(86_400);
        Instant rangeEnd = to != null ? to : rangeStart.plusSeconds(86_400 * 7);
        if (!rangeEnd.isAfter(rangeStart)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RANGE", "End must be after start");
        }
        return appointmentRepository.findByTenantIdAndWarehouseIdAndAppointmentStartBetweenOrderByAppointmentStartAsc(
                tenantId, warehouseId, rangeStart, rangeEnd);
    }

    @Transactional
    public DockAppointment updateStatus(UUID appointmentId, String status) {
        if (status == null || status.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "status is required");
        }
        DockAppointment appointment = requireAppointment(appointmentId);
        appointment.setStatus(status.trim().toUpperCase());
        return appointmentRepository.save(appointment);
    }

    private DockAppointment requireAppointment(UUID appointmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        return appointmentRepository.findByTenantIdAndId(tenantId, appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Appointment not found"));
    }

    private static void validateWindow(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WINDOW", "Start and end are required");
        }
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WINDOW", "End must be after start");
        }
    }

    public record ScheduleRequest(
            UUID warehouseId,
            int dockDoorNumber,
            UUID purchaseOrderId,
            String carrierName,
            String driverName,
            String truckLicensePlate,
            Instant appointmentStart,
            Instant appointmentEnd
    ) {
    }
}
