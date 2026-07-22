package com.invsys.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Near-real-time drain after each ledger commit so HTTP readers see levels promptly
 * without holding row locks during the original INSERT.
 */
@Component
public class InventoryLevelFlushOnCommitListener {

    private final InventoryLevelFlushWorker flushWorker;

    public InventoryLevelFlushOnCommitListener(InventoryLevelFlushWorker flushWorker) {
        this.flushWorker = flushWorker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLedgerCommitted(LedgerCommittedEvent event) {
        flushWorker.flushOnce();
    }
}
