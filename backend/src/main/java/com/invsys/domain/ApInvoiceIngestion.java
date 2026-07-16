package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ap_invoice_ingestions")
public class ApInvoiceIngestion extends TenantScopedEntity {

    @Column(name = "file_storage_key", nullable = false, length = 512)
    private String fileStorageKey;

    @Column(name = "ingestion_status", nullable = false, length = 50)
    private String ingestionStatus = "PROCESSING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_metadata", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> parsedMetadata = new LinkedHashMap<>();

    @Column(name = "matched_purchase_order_id")
    private UUID matchedPurchaseOrderId;

    public String getFileStorageKey() {
        return fileStorageKey;
    }

    public void setFileStorageKey(String fileStorageKey) {
        this.fileStorageKey = fileStorageKey;
    }

    public String getIngestionStatus() {
        return ingestionStatus;
    }

    public void setIngestionStatus(String ingestionStatus) {
        this.ingestionStatus = ingestionStatus;
    }

    public Map<String, Object> getParsedMetadata() {
        return parsedMetadata;
    }

    public void setParsedMetadata(Map<String, Object> parsedMetadata) {
        this.parsedMetadata = parsedMetadata;
    }

    public UUID getMatchedPurchaseOrderId() {
        return matchedPurchaseOrderId;
    }

    public void setMatchedPurchaseOrderId(UUID matchedPurchaseOrderId) {
        this.matchedPurchaseOrderId = matchedPurchaseOrderId;
    }
}
