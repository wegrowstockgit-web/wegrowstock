package com.invsys.repository;

import com.invsys.domain.DockAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DockAppointmentRepository extends JpaRepository<DockAppointment, UUID> {

    List<DockAppointment> findByTenantIdAndWarehouseIdAndAppointmentStartBetweenOrderByAppointmentStartAsc(
            UUID tenantId, UUID warehouseId, Instant from, Instant to);

    Optional<DockAppointment> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            SELECT d FROM DockAppointment d
            WHERE d.tenantId = :tenantId
              AND d.warehouseId = :warehouseId
              AND d.dockDoorNumber = :dockDoorNumber
              AND d.status NOT IN ('COMPLETED', 'NO_SHOW', 'CANCELLED')
              AND d.appointmentStart < :end
              AND d.appointmentEnd > :start
            """)
    List<DockAppointment> findOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("dockDoorNumber") int dockDoorNumber,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
