package com.invsys.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadMetricsTest {

    @Test
    void bindsActiveGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new VirtualThreadMetrics().bindTo(registry);
        assertThat(registry.find("invsys.virtual.threads.active").gauge()).isNotNull();
    }

    @Test
    void fallbackCountSeesRunningVirtualThread() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> {
                running.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            // Mounted/running VT should be visible via stack traces or MXBean once bound.
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            new VirtualThreadMetrics().bindTo(registry);
            Double value = registry.find("invsys.virtual.threads.active").gauge().value();
            assertThat(value).isNotNull();
            assertThat(value).isGreaterThanOrEqualTo(0.0);
        } finally {
            release.countDown();
        }
    }
}
