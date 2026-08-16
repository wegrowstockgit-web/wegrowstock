package com.invsys.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration
public class MetricsConfig {

    private static final Set<String> TENANT_KEYS = Set.of("tenant", "tenant_id");
    private static final Set<String> CUSTOMER_KEYS = Set.of("customer", "customer_name");

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Drops high-cardinality tenant identifiers and customer names from Prometheus tags.
     */
    @Bean
    public MeterFilter denySensitiveMeterTags() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                List<Tag> rewritten = new ArrayList<>();
                boolean changed = false;
                for (Tag tag : id.getTags()) {
                    String key = tag.getKey();
                    String value = tag.getValue();
                    if (TENANT_KEYS.contains(key)) {
                        String redacted = redactTenantTag(value);
                        if (!redacted.equals(value)) {
                            changed = true;
                        }
                        rewritten.add(Tag.of(key, redacted));
                    } else if (CUSTOMER_KEYS.contains(key)) {
                        changed = true;
                        rewritten.add(Tag.of(key, "redacted"));
                    } else {
                        rewritten.add(tag);
                    }
                }
                return changed ? id.replaceTags(Tags.of(rewritten)) : id;
            }
        };
    }

    static String redactTenantTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        if ("none".equals(value) || "unknown".equals(value) || "redacted".equals(value)) {
            return value;
        }
        try {
            UUID.fromString(value);
            return "redacted";
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
