package com.invsys.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Ensures monthly RANGE partitions exist for high-density telemetry tables.
 */
@Component
public class PartitionMaintenanceWorker {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceWorker.class);

    private final JdbcTemplate jdbcTemplate;

    public PartitionMaintenanceWorker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 15 0 1 * *")
    public void ensureForwardPartitions() {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(12);
        ensure("inventory_ledger", from, 19);
        ensure("audit_log", from, 19);
    }

    void ensure(String parentTable, LocalDate from, int months) {
        jdbcTemplate.execute((ConnectionCallback<Void>) con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    """
                    SELECT ensure_monthly_partitions(
                        to_regclass(?),
                        CAST(? AS date),
                        CAST(? AS integer)
                    )
                    """)) {
                ps.setString(1, "public." + parentTable);
                ps.setDate(2, Date.valueOf(from));
                ps.setInt(3, months);
                ps.execute();
            }
            return null;
        });
        log.info("Ensured {} monthly partitions for {} from {}", months, parentTable, from);
    }
}
