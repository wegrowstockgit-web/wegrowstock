package com.invsys.common;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Propagates correlation fields into MDC for request threads and background workers.
 */
public final class MdcSupport {

    public static final String REQUEST_ID = "requestId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";

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
            if (tenantId != null) {
                MDC.put(TENANT_ID, tenantId.toString());
            }
            if (requestId != null && !requestId.isBlank()) {
                MDC.put(REQUEST_ID, requestId);
            }
            if (userId != null) {
                MDC.put(USER_ID, userId.toString());
            }
            return action.get();
        } finally {
            if (previous != null) {
                MDC.setContextMap(previous);
            } else {
                MDC.clear();
            }
        }
    }

    public static String backgroundRequestId(String prefix, UUID id) {
        return prefix + "-" + (id != null ? id : UUID.randomUUID());
    }
}
