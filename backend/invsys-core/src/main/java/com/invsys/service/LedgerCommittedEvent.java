package com.invsys.service;

import java.util.UUID;

/** Fired after a ledger row is persisted so level deltas can flush post-commit. */
public record LedgerCommittedEvent(UUID tenantId, UUID ledgerId) {
}
