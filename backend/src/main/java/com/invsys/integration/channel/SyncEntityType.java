package com.invsys.integration.channel;

/**
 * Phase-1 hub entity types for {@code integration_sync_logs}.
 * Legacy rows may still store free-form values (e.g. {@code SALES_ORDER}, {@code LEDGER_ENTRY}).
 */
public enum SyncEntityType {
    ORDER,
    PRODUCT,
    SHIPMENT
}
