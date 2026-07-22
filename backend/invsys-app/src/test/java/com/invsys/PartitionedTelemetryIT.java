package com.invsys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class PartitionedTelemetryIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void ledgerAndAuditAreRangePartitionedWithMonthlyChildren() {
        Boolean ledgerPartitioned = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                  SELECT 1 FROM pg_partitioned_table pt
                  JOIN pg_class c ON c.oid = pt.partrelid
                  WHERE c.relname = 'inventory_ledger'
                )
                """,
                Boolean.class);
        Boolean auditPartitioned = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                  SELECT 1 FROM pg_partitioned_table pt
                  JOIN pg_class c ON c.oid = pt.partrelid
                  WHERE c.relname = 'audit_log'
                )
                """,
                Boolean.class);
        assertThat(ledgerPartitioned).isTrue();
        assertThat(auditPartitioned).isTrue();

        String month = LocalDate.now(ZoneOffset.UTC)
                .withDayOfMonth(1)
                .format(DateTimeFormatter.ofPattern("'y'yyyy'm'MM"));
        Integer ledgerParts = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_inherits i
                JOIN pg_class child ON child.oid = i.inhrelid
                JOIN pg_class parent ON parent.oid = i.inhparent
                WHERE parent.relname = 'inventory_ledger'
                  AND child.relname = ?
                """,
                Integer.class,
                "inventory_ledger_" + month);
        assertThat(ledgerParts).isEqualTo(1);

        Boolean compoundIndex = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                  SELECT 1 FROM pg_indexes
                  WHERE tablename = 'inventory_ledger'
                    AND indexdef ILIKE '%tenant_id%created_at%id%'
                )
                """,
                Boolean.class);
        assertThat(compoundIndex).isTrue();
    }

    @Test
    void ensureMonthlyPartitionsIsIdempotent() {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        jdbcTemplate.execute(
                "SELECT ensure_monthly_partitions(to_regclass('public.inventory_ledger'), DATE '"
                        + from + "', 3)");
        jdbcTemplate.execute(
                "SELECT ensure_monthly_partitions(to_regclass('public.inventory_ledger'), DATE '"
                        + from + "', 3)");
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_inherits i
                JOIN pg_class parent ON parent.oid = i.inhparent
                WHERE parent.relname = 'inventory_ledger'
                """,
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(3);
    }
}
