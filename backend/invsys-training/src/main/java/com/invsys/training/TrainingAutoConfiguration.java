package com.invsys.training;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

/**
 * Loads Flight Simulator / shadow-tenant beans only when training is enabled and this module
 * is on the classpath. {@code invsys-core} never depends on this module.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "invsys.features.training.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.invsys.training")
public class TrainingAutoConfiguration {
}
