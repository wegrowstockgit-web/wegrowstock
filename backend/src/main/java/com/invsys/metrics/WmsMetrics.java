package com.invsys.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Domain meters for WMS alerting (order throughput, allocation latency, API errors).
 */
@Component
public class WmsMetrics {

    public static final String ORDERS_PROCESSED = "wms.orders.processed";
    public static final String ALLOCATION_TIME = "wms.allocation.time";
    public static final String API_ERRORS = "wms.api.errors";
    public static final String AUDIT_ARCHIVE_FAILURES = "wms.audit.archive.failures";

    private final Counter ordersProcessed;
    private final Timer allocationTime;
    private final MeterRegistry registry;

    public WmsMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ordersProcessed = Counter.builder(ORDERS_PROCESSED)
                .description("Sales orders successfully allocated / processed")
                .register(registry);
        this.allocationTime = Timer.builder(ALLOCATION_TIME)
                .description("Wall time to allocate a sales order")
                .publishPercentileHistogram()
                .register(registry);
        Counter.builder(AUDIT_ARCHIVE_FAILURES)
                .description("Cold audit archival upload/purge failures by tenant")
                .tag("tenant", "none")
                .register(registry);
    }

    public void incrementOrdersProcessed() {
        ordersProcessed.increment();
    }

    public Timer.Sample startAllocation() {
        return Timer.start(registry);
    }

    public void stopAllocation(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(allocationTime);
        }
    }

    public void recordAllocation(long durationNanos) {
        allocationTime.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void incrementApiError(String endpoint) {
        String tag = endpoint == null || endpoint.isBlank() ? "unknown" : endpoint;
        Counter.builder(API_ERRORS)
                .description("API errors by endpoint")
                .tag("endpoint", tag)
                .register(registry)
                .increment();
    }

    public void incrementAuditArchiveFailure(java.util.UUID tenantId) {
        String tag = tenantId == null ? "unknown" : tenantId.toString();
        Counter.builder(AUDIT_ARCHIVE_FAILURES)
                .description("Cold audit archival upload/purge failures by tenant")
                .tag("tenant", tag)
                .register(registry)
                .increment();
    }
}
