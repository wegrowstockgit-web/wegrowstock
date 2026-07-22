package com.invsys.core.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnProperty(name = "invsys.integration.outbox.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
class OutboxPollingConfig {

    private final OutboxDispatcher outboxDispatcher;

    OutboxPollingConfig(OutboxDispatcher outboxDispatcher) {
        this.outboxDispatcher = outboxDispatcher;
    }

    @Scheduled(fixedDelayString = "${invsys.integration.outbox.poll-interval-ms:5000}")
    void pollOutbox() {
        outboxDispatcher.dispatch();
    }
}
