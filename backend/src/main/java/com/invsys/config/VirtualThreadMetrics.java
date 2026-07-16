package com.invsys.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.PlatformManagedObject;
import java.lang.reflect.Method;

/**
 * Virtual-thread gauges for Grafana.
 * Prefers {@code jdk.management.VirtualThreadSchedulerMXBean} when present (JDK 24+),
 * otherwise falls back to an approximate live count via {@link Thread#getAllStackTraces()}.
 */
@Component
public class VirtualThreadMetrics implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        Object scheduler = resolveSchedulerMxBean();
        if (scheduler != null) {
            Method mounted = findMethod(scheduler.getClass(), "getMountedVirtualThreadCount");
            Method queued = findMethod(scheduler.getClass(), "getQueuedVirtualThreadCount");
            if (mounted != null) {
                Gauge.builder("invsys.virtual.threads.active", scheduler, bean -> invokeLong(mounted, bean))
                        .description("Mounted virtual threads (VirtualThreadSchedulerMXBean)")
                        .register(registry);
            }
            if (queued != null) {
                Gauge.builder("invsys.virtual.threads.queued", scheduler, bean -> invokeLong(queued, bean))
                        .description("Queued virtual threads (VirtualThreadSchedulerMXBean)")
                        .register(registry);
            }
            return;
        }

        Gauge.builder("invsys.virtual.threads.active", VirtualThreadMetrics::countLiveVirtualThreads)
                .description("Approximate number of live JVM virtual threads")
                .register(registry);
    }

    static double countLiveVirtualThreads() {
        long count = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isVirtual()) {
                count++;
            }
        }
        return count;
    }

    private static Object resolveSchedulerMxBean() {
        try {
            Class<?> type = Class.forName("jdk.management.VirtualThreadSchedulerMXBean");
            if (!PlatformManagedObject.class.isAssignableFrom(type)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Class<? extends PlatformManagedObject> mxType = (Class<? extends PlatformManagedObject>) type;
            return ManagementFactory.getPlatformMXBean(mxType);
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private static double invokeLong(Method method, Object target) {
        try {
            Object value = method.invoke(target);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return 0;
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }
}
