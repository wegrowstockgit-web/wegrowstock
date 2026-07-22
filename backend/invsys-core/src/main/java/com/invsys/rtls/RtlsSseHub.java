package com.invsys.rtls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High-throughput fan-out for RTLS position frames (SSE streaming edge).
 */
@Component
public class RtlsSseHub {

    private static final Logger log = LoggerFactory.getLogger(RtlsSseHub.class);
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tenantId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(tenantId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(tenantId, emitter));
        emitter.onTimeout(() -> remove(tenantId, emitter));
        emitter.onError(ex -> remove(tenantId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("tenantId", tenantId.toString())));
        } catch (IOException ignored) {
            remove(tenantId, emitter);
        }
        return emitter;
    }

    public void publish(UUID tenantId, Map<String, Object> frame) {
        List<SseEmitter> list = emitters.get(tenantId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("rtls.position").data(frame));
            } catch (Exception ex) {
                remove(tenantId, emitter);
                log.debug("RTLS SSE drop tenant={}: {}", tenantId, ex.getMessage());
            }
        }
    }

    private void remove(UUID tenantId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(tenantId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
