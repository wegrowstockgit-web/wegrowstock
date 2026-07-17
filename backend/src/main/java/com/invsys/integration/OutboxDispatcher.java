package com.invsys.integration;

import com.invsys.common.MdcSupport;
import com.invsys.config.IntegrationProperties;
import com.invsys.repository.OutboxEventRepository;
import com.invsys.repository.OutboxEventRepositoryCustom.ClaimedOutboxEvent;
import com.invsys.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "invsys.integration.outbox.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final IntegrationProperties integrationProperties;
    private final Map<String, OutboxEventHandler> handlers;
    private final ExecutorService virtualThreadExecutor;
    private final ApplicationEventPublisher eventPublisher;

    public OutboxDispatcher(
            OutboxEventRepository outboxEventRepository,
            IntegrationProperties integrationProperties,
            List<OutboxEventHandler> handlerList,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor,
            ApplicationEventPublisher eventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.integrationProperties = integrationProperties;
        this.handlers = handlerList.stream()
                .flatMap(handler -> handler.eventTypes().stream().map(type -> Map.entry(type, handler)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.eventPublisher = eventPublisher;
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
                () -> processClaimed(event));
    }

    private boolean processClaimed(ClaimedOutboxEvent event) {
        TenantContext.setTenantId(event.tenantId());
        try {
            OutboxEventHandler handler = handlers.get(event.eventType());
            if (handler == null) {
                log.warn("No handler for outbox event type={} id={}", event.eventType(), event.id());
                outboxEventRepository.markPublished(event.id());
                publishDispatched(event);
                return true;
            }
            Exception handlerFailure = runHandlerOnVirtualThread(event, handler);
            if (handlerFailure != null) {
                log.error("Outbox dispatch failed id={} type={}", event.id(), event.eventType(), handlerFailure);
                handleFailure(event, handlerFailure);
                return true;
            }
            outboxEventRepository.markPublished(event.id());
            publishDispatched(event);
            return true;
        } finally {
            TenantContext.clear();
            MDC.clear();
        }
    }

    private void publishDispatched(ClaimedOutboxEvent event) {
        eventPublisher.publishEvent(new OutboxDispatchedEvent(
                event.tenantId(),
                event.id(),
                event.eventType(),
                event.aggregateId(),
                event.payload()));
    }

    private Exception runHandlerOnVirtualThread(ClaimedOutboxEvent event, OutboxEventHandler handler) {
        Future<?> future = virtualThreadExecutor.submit(MdcSupport.wrapWithContext(() -> {
            TenantContext.setTenantId(event.tenantId());
            try {
                handler.handle(event.tenantId(), event.aggregateId(), event.eventType(), event.payload());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                // Neutralize ThreadLocal / MDC residue on reused virtual-thread frames.
                TenantContext.clear();
                MDC.clear();
            }
        }));
        try {
            future.get();
            return null;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new IllegalStateException("Outbox handler interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof RuntimeException re && re.getCause() instanceof Exception nested) {
                return nested;
            }
            if (cause instanceof Exception ex) {
                return ex;
            }
            return new IllegalStateException(cause);
        }
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
