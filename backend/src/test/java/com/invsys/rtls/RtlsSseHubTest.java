package com.invsys.rtls;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class RtlsSseHubTest {

    @Test
    void subscribePublishAndCleanup() {
        RtlsSseHub hub = new RtlsSseHub();
        UUID tenantId = UUID.randomUUID();

        SseEmitter emitter = hub.subscribe(tenantId);
        assertThatCode(() -> hub.publish(tenantId, Map.of("tagId", "T-1", "x", 1.5, "y", 2.5)))
                .doesNotThrowAnyException();
        assertThatCode(() -> hub.publish(UUID.randomUUID(), Map.of("tagId", "noop")))
                .doesNotThrowAnyException();

        emitter.complete();
        assertThatCode(() -> hub.publish(tenantId, Map.of("tagId", "after-complete")))
                .doesNotThrowAnyException();

        SseEmitter again = hub.subscribe(tenantId);
        assertThatCode(() -> hub.publish(tenantId, Map.of("tagId", "T-2")))
                .doesNotThrowAnyException();
        again.complete();
    }
}
