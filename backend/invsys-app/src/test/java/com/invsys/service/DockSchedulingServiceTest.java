package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.DockAppointment;
import com.invsys.repository.DockAppointmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockSchedulingServiceTest {

    private static final UUID TENANT = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID WAREHOUSE = UUID.fromString("b0000000-0000-4000-8000-000000000604");

    @Mock DockAppointmentRepository appointmentRepository;

    private DockSchedulingService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new DockSchedulingService(appointmentRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsOverlappingAppointments() {
        Instant start = Instant.parse("2026-07-23T10:00:00Z");
        Instant end = Instant.parse("2026-07-23T11:00:00Z");

        DockAppointment existing = new DockAppointment();
        existing.setId(UUID.randomUUID());
        when(appointmentRepository.findOverlapping(TENANT, WAREHOUSE, 3, start, end))
                .thenReturn(List.of(existing));

        DockSchedulingService.ScheduleRequest request = new DockSchedulingService.ScheduleRequest(
                WAREHOUSE, 3, null, "FedEx", "Driver", "ABC123", start, end);

        assertThatThrownBy(() -> service.scheduleAppointment(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getCode()).isEqualTo("CONFLICT");
                });

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void schedulesWhenNoCollision() {
        Instant start = Instant.parse("2026-07-23T14:00:00Z");
        Instant end = Instant.parse("2026-07-23T15:00:00Z");

        when(appointmentRepository.findOverlapping(TENANT, WAREHOUSE, 1, start, end))
                .thenReturn(List.of());
        when(appointmentRepository.save(any(DockAppointment.class))).thenAnswer(inv -> {
            DockAppointment saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        DockAppointment result = service.scheduleAppointment(new DockSchedulingService.ScheduleRequest(
                WAREHOUSE, 1, null, "UPS", "Jane", "XYZ789", start, end));

        assertThat(result.getStatus()).isEqualTo("SCHEDULED");
        assertThat(result.getDockDoorNumber()).isEqualTo(1);

        ArgumentCaptor<DockAppointment> captor = ArgumentCaptor.forClass(DockAppointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().getWarehouseId()).isEqualTo(WAREHOUSE);
    }

    @Test
    void checkInSetsCheckedInStatus() {
        UUID appointmentId = UUID.randomUUID();
        DockAppointment appointment = new DockAppointment();
        appointment.setId(appointmentId);
        appointment.setTenantId(TENANT);
        appointment.setStatus("SCHEDULED");

        when(appointmentRepository.findByTenantIdAndId(TENANT, appointmentId))
                .thenReturn(java.util.Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        DockAppointment checkedIn = service.checkInDriver(appointmentId);

        assertThat(checkedIn.getStatus()).isEqualTo("CHECKED_IN");
    }
}
