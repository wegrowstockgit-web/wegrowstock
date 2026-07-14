package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ap_matching_logs")
public class ApMatchingLog extends TenantScopedEntity {

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "ingestion_id")
    private UUID ingestionId;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "match_status", nullable = false, length = 30)
    private String matchStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> validationErrors = new ArrayList<>();

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public UUID getIngestionId() {
        return ingestionId;
    }

    public void setIngestionId(UUID ingestionId) {
        this.ingestionId = ingestionId;
    }

    public UUID getPoId() {
        return poId;
    }

    public void setPoId(UUID poId) {
        this.poId = poId;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(String matchStatus) {
        this.matchStatus = matchStatus;
    }

    public List<Map<String, Object>> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<Map<String, Object>> validationErrors) {
        this.validationErrors = validationErrors;
    }
}
