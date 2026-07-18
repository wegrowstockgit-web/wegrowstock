package com.invsys.api.dto;

/**
 * Pre-flight classification for a single CSV import row.
 * Rows that are not {@link #READY_TO_IMPORT} are never written to the inventory ledger
 * unless the caller explicitly resolves missing references first.
 */
public enum ImportRowStatus {
    /** Product (and location/UOM) resolved; dimensions valid — safe to receive. */
    READY_TO_IMPORT,
    /** SKU does not exist in the tenant catalog. */
    MISSING_PRODUCT,
    /** location_path / fallback warehouse could not be resolved. */
    MISSING_LOCATION,
    /** Declared UOM is not in the tenant allow-list. */
    MISSING_UOM,
    /** Row failed structural checks (qty, required dimensions, temp zone, etc.). */
    VALIDATION_ERROR
}
