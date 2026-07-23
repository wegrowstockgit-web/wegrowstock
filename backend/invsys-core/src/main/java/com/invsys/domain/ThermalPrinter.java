package com.invsys.domain;

import com.invsys.core.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "thermal_printers")
public class ThermalPrinter extends TenantScopedEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "printer_type", nullable = false, length = 32)
    private String printerType;

    @Column(name = "printnode_printer_id", length = 64)
    private String printnodePrinterId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    private Integer port;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "location_id")
    private UUID locationId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrinterType() {
        return printerType;
    }

    public void setPrinterType(String printerType) {
        this.printerType = printerType;
    }

    public String getPrintnodePrinterId() {
        return printnodePrinterId;
    }

    public void setPrintnodePrinterId(String printnodePrinterId) {
        this.printnodePrinterId = printnodePrinterId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }
}
