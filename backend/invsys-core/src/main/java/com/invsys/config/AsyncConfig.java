package com.invsys.config;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Virtual-thread executors with Micrometer ContextSnapshot propagation so
 * {@code trace_id} / MDC survive hops from HTTP threads into async workers.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }

    @Bean(name = "virtualThreadExecutor", destroyMethod = "shutdown")
    public ExecutorService virtualThreadExecutor(MeterRegistry registry) {
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        ContextSnapshotFactory factory = ContextSnapshotFactory.builder().build();
        ExecutorService contextAware = ContextExecutorService.wrap(delegate, factory::captureAll);
        return ExecutorServiceMetrics.monitor(registry, contextAware, "invsys.virtual");
    }
}
