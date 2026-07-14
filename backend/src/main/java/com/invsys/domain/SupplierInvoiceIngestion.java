package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "supplier_invoice_ingestions")
public class SupplierInvoiceIngestion extends TenantScopedEntity {

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @Column(nullable = false)
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> extractedData = new LinkedHashMap<>();

    @Column(name = "match_confidence", nullable = false)
    private BigDecimal matchConfidence = BigDecimal.ZERO;

    public UUID getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(UUID purchaseOrderId) {
        this.purchaseOrderId = purchaseOrderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getExtractedData() {
        return extractedData;
    }

    public void setExtractedData(Map<String, Object> extractedData) {
        this.extractedData = extractedData;
    }

    public BigDecimal getMatchConfidence() {
        return matchConfidence;
    }

    public void setMatchConfidence(BigDecimal matchConfidence) {
        this.matchConfidence = matchConfidence;
    }
}
