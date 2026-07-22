package com.invsys.domain;

/**
 * Floor mutation families that can park in {@code offline_sync_conflicts}.
 */
public enum ConflictActionType {
    INBOUND_RECEIVE,
    OUTBOUND_PICK,
    CYCLE_COUNT;

    public static ConflictActionType fromScanMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        return switch (mode.trim().toLowerCase()) {
            case "receive" -> INBOUND_RECEIVE;
            case "pick" -> OUTBOUND_PICK;
            case "count" -> CYCLE_COUNT;
            default -> null;
        };
    }

    public String humanLabel() {
        return switch (this) {
            case INBOUND_RECEIVE -> "Inbound Receive";
            case OUTBOUND_PICK -> "Outbound Pick";
            case CYCLE_COUNT -> "Cycle Count";
        };
    }
}
