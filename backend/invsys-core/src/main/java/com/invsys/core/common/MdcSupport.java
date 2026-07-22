package com.invsys.core.common;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Propagates correlation fields into MDC for request threads and background workers.
 * Keys follow the observability contract: {@code request_id}, {@code tenant_id}, {@code user_id}.
 */
public final class MdcSupport {

    public static final String REQUEST_ID = "request_id";
    public static final String TENANT_ID = "tenant_id";
    public static final String USER_ID = "user_id";

    private static final ContextSnapshotFactory SNAPSHOT_FACTORY = ContextSnapshotFactory.builder().build();

    private MdcSupport() {
    }

    public static void run(UUID tenantId, String requestId, Runnable action) {
        run(tenantId, requestId, null, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T run(UUID tenantId, String requestId, UUID userId, Supplier<T> action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            putCorrelation(tenantId, requestId, userId);
            return action.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * Capture Micrometer/OTel + MDC context from the caller and reopen it on the worker thread.
     */
    public static Runnable wrapWithContext(Runnable action) {
        ContextSnapshot snapshot = SNAPSHOT_FACTORY.captureAll();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            try (ContextSnapshot.Scope ignored = snapshot.setThreadLocals()) {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                action.run();
            } finally {
                MDC.clear();
            }
        };
    }

    public static <T> Callable<T> wrapWithContext(Callable<T> action) {
        ContextSnapshot snapshot = SNAPSHOT_FACTORY.captureAll();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            try (ContextSnapshot.Scope ignored = snapshot.setThreadLocals()) {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                return action.call();
            } finally {
                MDC.clear();
            }
        };
    }

    public static String backgroundRequestId(String prefix, UUID id) {
        return prefix + "-" + (id != null ? id : UUID.randomUUID());
    }

    private static void putCorrelation(UUID tenantId, String requestId, UUID userId) {
        if (tenantId != null) {
            MDC.put(TENANT_ID, tenantId.toString());
            MDC.put("tenantId", tenantId.toString());
        }
        if (requestId != null && !requestId.isBlank()) {
            MDC.put(REQUEST_ID, requestId);
            MDC.put("requestId", requestId);
        }
        if (userId != null) {
            MDC.put(USER_ID, userId.toString());
            MDC.put("userId", userId.toString());
        }
    }

    private static void restore(Map<String, String> previous) {
        if (previous != null) {
            MDC.setContextMap(previous);
        } else {
            MDC.clear();
        }
    }
}
