package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "offline_sync_conflicts")
public class OfflineSyncConflict extends TenantScopedEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DISCARDED = "DISCARDED";
    public static final String STATUS_RESOLVED_AND_REPLAYED = "RESOLVED_AND_REPLAYED";
    /** @deprecated use {@link #STATUS_DISCARDED} */
    public static final String STATUS_DISMISSED = "DISMISSED";
    /** @deprecated use {@link #STATUS_RESOLVED_AND_REPLAYED} */
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_RETRY_REQUESTED = "RETRY_REQUESTED";

    public static final String REASON_OFFLINE_CONFLICT_OVERRIDE = "OFFLINE_CONFLICT_OVERRIDE";

    @Column(name = "picker_user_id")
    private UUID pickerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 40)
    private ConflictActionType actionType;

    @Column(name = "request_url", length = 512)
    private String requestUrl;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(nullable = false, length = 40)
    private String status = STATUS_PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_metadata_json", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> schemaMetadata = new ArrayList<>();

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public UUID getPickerUserId() {
        return pickerUserId;
    }

    public void setPickerUserId(UUID pickerUserId) {
        this.pickerUserId = pickerUserId;
    }

    public ConflictActionType getActionType() {
        return actionType;
    }

    public void setActionType(ConflictActionType actionType) {
        this.actionType = actionType;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload != null ? payload : new LinkedHashMap<>();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Map<String, Object>> getSchemaMetadata() {
        return schemaMetadata;
    }

    public void setSchemaMetadata(List<Map<String, Object>> schemaMetadata) {
        this.schemaMetadata = schemaMetadata != null ? schemaMetadata : new ArrayList<>();
    }

    public UUID getResolvedByUserId() {
        return resolvedByUserId;
    }

    public void setResolvedByUserId(UUID resolvedByUserId) {
        this.resolvedByUserId = resolvedByUserId;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
