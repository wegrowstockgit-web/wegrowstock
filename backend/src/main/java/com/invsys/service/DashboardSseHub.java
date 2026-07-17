package com.invsys.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-process fan-out of dashboard SSE events, keyed by tenant.
 */
@Component
public class DashboardSseHub {

    private static final Logger log = LoggerFactory.getLogger(DashboardSseHub.class);
    private static final long DEFAULT_TIMEOUT_MS = 30L * 60L * 1000L;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByTenant =
            new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tenantId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> bucket = emittersByTenant.computeIfAbsent(
                tenantId, id -> new CopyOnWriteArrayList<>());
        bucket.add(emitter);

        Runnable remove = () -> {
            bucket.remove(emitter);
            if (bucket.isEmpty()) {
                emittersByTenant.remove(tenantId, bucket);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ex -> remove.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "tenantId", tenantId.toString(),
                            "at", Instant.now().toString())));
        } catch (IOException ex) {
            remove.run();
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void broadcast(UUID tenantId, String eventType, Map<String, Object> payload) {
        List<SseEmitter> emitters = emittersByTenant.get(tenantId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        Map<String, Object> body = Map.of(
                "eventType", eventType,
                "payload", payload != null ? payload : Map.of(),
                "at", Instant.now().toString());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("dashboard").data(body));
            } catch (Exception ex) {
                log.debug("Removing stale SSE emitter tenant={}: {}", tenantId, ex.toString());
                emitters.remove(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // already closed
                }
            }
        }
    }
}
