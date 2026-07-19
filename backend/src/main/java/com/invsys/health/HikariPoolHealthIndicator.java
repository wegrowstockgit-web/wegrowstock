package com.invsys.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Reports HikariCP pool liveness plus active / idle / waiting connection counts.
 */
@Component("hikari")
public class HikariPoolHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public HikariPoolHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection ignored = dataSource.getConnection()) {
            Health.Builder builder = Health.up();
            HikariDataSource hikari = unwrapHikari(dataSource);
            if (hikari != null) {
                builder.withDetail("poolName", hikari.getPoolName())
                        .withDetail("maximumPoolSize", hikari.getMaximumPoolSize())
                        .withDetail("minimumIdle", hikari.getMinimumIdle());
                HikariPoolMXBean mx = hikari.getHikariPoolMXBean();
                if (mx != null) {
                    builder.withDetail("active", mx.getActiveConnections())
                            .withDetail("idle", mx.getIdleConnections())
                            .withDetail("total", mx.getTotalConnections())
                            .withDetail("threadsAwaitingConnection", mx.getThreadsAwaitingConnection());
                }
            }
            return builder.build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }

    private static HikariDataSource unwrapHikari(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari;
        }
        try {
            return dataSource.unwrap(HikariDataSource.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
