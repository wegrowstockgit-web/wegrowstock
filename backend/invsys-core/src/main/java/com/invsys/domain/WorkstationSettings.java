package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "workstation_settings")
public class WorkstationSettings extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "print_mode", nullable = false)
    private String printMode = "PDF";

    @Column(name = "zpl_printer_name")
    private String zplPrinterName;

    @Column(name = "label_format", nullable = false)
    private String labelFormat = "4x6";

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getPrintMode() {
        return printMode;
    }

    public void setPrintMode(String printMode) {
        this.printMode = printMode;
    }

    public String getZplPrinterName() {
        return zplPrinterName;
    }

    public void setZplPrinterName(String zplPrinterName) {
        this.zplPrinterName = zplPrinterName;
    }

    public String getLabelFormat() {
        return labelFormat;
    }

    public void setLabelFormat(String labelFormat) {
        this.labelFormat = labelFormat;
    }
}
