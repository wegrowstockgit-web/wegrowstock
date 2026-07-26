package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "dock_appointments")
public class DockAppointment extends TenantScopedEntity {

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "dock_door_number", nullable = false)
    private int dockDoorNumber;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "truck_license_plate", length = 50)
    private String truckLicensePlate;

    @Column(name = "appointment_start", nullable = false)
    private Instant appointmentStart;

    @Column(name = "appointment_end", nullable = false)
    private Instant appointmentEnd;

    @Column(nullable = false, length = 32)
    private String status = "SCHEDULED";

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public int getDockDoorNumber() {
        return dockDoorNumber;
    }

    public void setDockDoorNumber(int dockDoorNumber) {
        this.dockDoorNumber = dockDoorNumber;
    }

    public UUID getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(UUID purchaseOrderId) {
        this.purchaseOrderId = purchaseOrderId;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public String getTruckLicensePlate() {
        return truckLicensePlate;
    }

    public void setTruckLicensePlate(String truckLicensePlate) {
        this.truckLicensePlate = truckLicensePlate;
    }

    public Instant getAppointmentStart() {
        return appointmentStart;
    }

    public void setAppointmentStart(Instant appointmentStart) {
        this.appointmentStart = appointmentStart;
    }

    public Instant getAppointmentEnd() {
        return appointmentEnd;
    }

    public void setAppointmentEnd(Instant appointmentEnd) {
        this.appointmentEnd = appointmentEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
