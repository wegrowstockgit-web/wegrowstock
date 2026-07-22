package com.invsys.integration.channel;

/**
 * Phase-1 sync outcomes. Legacy rows also use {@code PENDING}/{@code SYNCED}/{@code SKIPPED}.
 */
public enum SyncLogStatus {
    SUCCESS,
    FAILED,
    WARNING,
    PENDING,
    SYNCED,
    SKIPPED
}
