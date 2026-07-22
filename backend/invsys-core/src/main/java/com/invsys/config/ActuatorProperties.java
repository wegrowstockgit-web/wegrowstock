package com.invsys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "invsys.actuator")
public class ActuatorProperties {

    private static final String DEFAULT_CIDRS =
            "127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";

    /**
     * Comma-separated CIDRs allowed to scrape {@code /actuator/health} and
     * {@code /actuator/prometheus} without authentication.
     */
    private String scrapeAllowedCidrs = DEFAULT_CIDRS;

    public String getScrapeAllowedCidrs() {
        return scrapeAllowedCidrs;
    }

    public void setScrapeAllowedCidrs(String scrapeAllowedCidrs) {
        this.scrapeAllowedCidrs = scrapeAllowedCidrs != null && !scrapeAllowedCidrs.isBlank()
                ? scrapeAllowedCidrs
                : DEFAULT_CIDRS;
    }

    public List<String> resolvedScrapeAllowedCidrs() {
        List<String> out = new ArrayList<>();
        Arrays.stream(scrapeAllowedCidrs.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(out::add);
        return out.isEmpty() ? List.of(DEFAULT_CIDRS.split(",")) : List.copyOf(out);
    }
}
