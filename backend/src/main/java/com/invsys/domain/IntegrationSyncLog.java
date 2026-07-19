package com.invsys.domain;

import com.invsys.integration.channel.SyncDirection;
import com.invsys.integration.channel.SyncLogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "integration_sync_logs")
public class IntegrationSyncLog extends TenantScopedEntity {

    /** Legacy system key (SHOPIFY, XERO, …); mirrored from channel type when channel-linked. */
    @Column(nullable = false)
    private String system;

    @Column(name = "channel_id")
    private UUID channelId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SyncDirection direction;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "external_id")
    private String externalId;

    /**
     * Stored as VARCHAR; prefer {@link SyncLogStatus} names for new writes.
     * Legacy values: PENDING, SYNCED, SKIPPED, FAILED.
     */
    @Column(nullable = false)
    private String status = SyncLogStatus.PENDING.name();

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "error_message")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_summary", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payloadSummary = new LinkedHashMap<>();

    @Column(name = "processed_at")
    private Instant processedAt;

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public void setChannelId(UUID channelId) {
        this.channelId = channelId;
    }

    public SyncDirection getDirection() {
        return direction;
    }

    public void setDirection(SyncDirection direction) {
        this.direction = direction;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
        this.errorMessage = lastError;
    }

    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : lastError;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.lastError = errorMessage;
    }

    public Map<String, Object> getPayloadSummary() {
        return payloadSummary;
    }

    public void setPayloadSummary(Map<String, Object> payloadSummary) {
        this.payloadSummary = payloadSummary != null ? payloadSummary : new LinkedHashMap<>();
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
