package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "edi_document_logs")
public class EdiDocumentLog extends TenantScopedEntity {

    @Column(name = "trading_partner_id", nullable = false)
    private UUID tradingPartnerId;

    @Column(nullable = false)
    private String direction;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private String status = "PENDING";

    public UUID getTradingPartnerId() {
        return tradingPartnerId;
    }

    public void setTradingPartnerId(UUID tradingPartnerId) {
        this.tradingPartnerId = tradingPartnerId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
