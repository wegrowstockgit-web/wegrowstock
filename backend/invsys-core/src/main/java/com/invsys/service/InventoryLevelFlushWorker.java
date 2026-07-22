package com.invsys.service;

import com.invsys.modules.inventory.repository.InventoryLevelDeltaFlushRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

/**
 * Lock-free drain of {@code inventory_level_deltas} into {@code inventory_levels}
 * on Java virtual threads. Claim uses FOR UPDATE SKIP LOCKED so multiple instances
 * can flush without hotspot contention on level rows during ledger inserts.
 */
@Component
@ConditionalOnProperty(name = "invsys.inventory.level-flush.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryLevelFlushWorker {

    private static final Logger log = LoggerFactory.getLogger(InventoryLevelFlushWorker.class);

    private final InventoryLevelDeltaFlushRepository flushRepository;
    private final ExecutorService virtualThreadExecutor;
    private final int batchSize;

    public InventoryLevelFlushWorker(
            InventoryLevelDeltaFlushRepository flushRepository,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor,
            @Value("${invsys.inventory.level-flush.batch-size:200}") int batchSize) {
        this.flushRepository = flushRepository;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${invsys.inventory.level-flush.poll-interval-ms:100}")
    public void scheduleFlush() {
        virtualThreadExecutor.execute(this::flushOnce);
    }

    public void flushOnce() {
        try {
            int flushed = flushRepository.flushBatch(batchSize);
            if (flushed > 0 && log.isDebugEnabled()) {
                log.debug("Flushed {} inventory level deltas", flushed);
            }
        } catch (Exception ex) {
            log.warn("Inventory level delta flush failed: {}", ex.toString());
        }
    }
}
