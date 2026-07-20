package com.invsys.api.dto;

import com.invsys.domain.ConflictActionType;
import com.invsys.domain.OfflineSyncConflict;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Office-facing conflict projection — never expose raw serialization jargon in copy fields.
 */
public record OfflineSyncConflictView(
        UUID id,
        UUID tenantId,
        UUID pickerUserId,
        String pickerDisplayName,
        ConflictActionType actionType,
        String actionLabel,
        String requestUrl,
        String errorMessage,
        String humanSummary,
        String status,
        Map<String, Object> payload,
        List<Map<String, Object>> schemaMetadata,
        UUID resolvedByUserId,
        Instant createdAt,
        Instant resolvedAt
) {
    public static OfflineSyncConflictView from(OfflineSyncConflict row, String pickerDisplayName) {
        ConflictActionType action = row.getActionType();
        String actionLabel = action != null ? action.humanLabel() : "Warehouse Transaction";
        String operator = pickerDisplayName != null && !pickerDisplayName.isBlank()
                ? pickerDisplayName
                : "Floor Operator";
        String reason = row.getErrorMessage() != null && !row.getErrorMessage().isBlank()
                ? humanizeError(row.getErrorMessage())
                : "a business rule blocked the replay";
        String summary = "Floor Operator [" + operator + "] failed to process an [" + actionLabel
                + "] transaction because " + reason + ".";
        return new OfflineSyncConflictView(
                row.getId(),
                row.getTenantId(),
                row.getPickerUserId(),
                operator,
                action,
                actionLabel,
                row.getRequestUrl() != null ? row.getRequestUrl() : stringPayload(row, "url"),
                row.getErrorMessage(),
                summary,
                row.getStatus(),
                row.getPayload(),
                row.getSchemaMetadata(),
                row.getResolvedByUserId(),
                row.getCreatedAt(),
                row.getResolvedAt());
    }

    private static String stringPayload(OfflineSyncConflict row, String key) {
        Object v = row.getPayload() != null ? row.getPayload().get(key) : null;
        return v != null ? String.valueOf(v) : null;
    }

    private static String humanizeError(String raw) {
        String trimmed = raw.strip();
        // Strip leading ERROR_CODE: prefixes for managers.
        int colon = trimmed.indexOf(':');
        if (colon > 0 && colon < 48 && trimmed.substring(0, colon).matches("[A-Z0-9_]+")) {
            trimmed = trimmed.substring(colon + 1).strip();
        }
        if (trimmed.isEmpty()) {
            return "a business rule blocked the replay";
        }
        // Ensure sentence-friendly lowercase start after "because".
        char first = trimmed.charAt(0);
        if (Character.isUpperCase(first)) {
            trimmed = Character.toLowerCase(first) + trimmed.substring(1);
        }
        if (!trimmed.endsWith(".") && !trimmed.endsWith("!") && !trimmed.endsWith("?")) {
            // leave as clause
        }
        return trimmed;
    }
}
