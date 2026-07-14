package com.invsys.integration;

import com.invsys.common.MdcSupport;
import com.invsys.config.IntegrationProperties;
import com.invsys.repository.OutboxEventRepository;
import com.invsys.repository.OutboxEventRepositoryCustom.ClaimedOutboxEvent;
import com.invsys.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "invsys.integration.outbox.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final IntegrationProperties integrationProperties;
    private final Map<String, OutboxEventHandler> handlers;

    public OutboxDispatcher(
            OutboxEventRepository outboxEventRepository,
            IntegrationProperties integrationProperties,
            List<OutboxEventHandler> handlerList) {
        this.outboxEventRepository = outboxEventRepository;
        this.integrationProperties = integrationProperties;
        this.handlers = handlerList.stream()
                .flatMap(handler -> handler.eventTypes().stream().map(type -> Map.entry(type, handler)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
    }

    public void dispatch() {
        int batchSize = integrationProperties.getOutbox().getBatchSize();
        for (int i = 0; i < batchSize; i++) {
            if (!dispatchNext()) {
                break;
            }
        }
    }

    @Transactional
    public boolean dispatchNext() {
        List<ClaimedOutboxEvent> claimed = outboxEventRepository.claimPendingEvents(1);
        if (claimed.isEmpty()) {
            return false;
        }
        ClaimedOutboxEvent event = claimed.get(0);
        return MdcSupport.run(
                event.tenantId(),
                MdcSupport.backgroundRequestId("outbox", event.id()),
                null,
                () -> {
                    TenantContext.setTenantId(event.tenantId());
                    try {
                        OutboxEventHandler handler = handlers.get(event.eventType());
                        if (handler == null) {
                            log.warn("No handler for outbox event type={} id={}", event.eventType(), event.id());
                            outboxEventRepository.markPublished(event.id());
                            return true;
                        }
                        handler.handle(event.tenantId(), event.aggregateId(), event.eventType(), event.payload());
                        outboxEventRepository.markPublished(event.id());
                        return true;
                    } catch (Exception e) {
                        log.error("Outbox dispatch failed id={} type={}", event.id(), event.eventType(), e);
                        handleFailure(event, e);
                        return true;
                    } finally {
                        TenantContext.clear();
                    }
                });
    }

    private void handleFailure(ClaimedOutboxEvent event, Exception e) {
        int maxRetries = integrationProperties.getOutbox().getMaxRetries();
        int nextRetry = event.retryCount() + 1;
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (nextRetry >= maxRetries) {
            outboxEventRepository.markFailed(event.id(), nextRetry, message, null, "FAILED");
        } else {
            Instant nextAttempt = Instant.now().plusSeconds(backoffSeconds(nextRetry));
            outboxEventRepository.markFailed(event.id(), nextRetry, message, nextAttempt, "PENDING");
        }
    }

    private long backoffSeconds(int retryCount) {
        long base = Math.min(300L, 1L << Math.min(retryCount, 8));
        return base + ThreadLocalRandom.current().nextLong(0, 5);
    }
}
