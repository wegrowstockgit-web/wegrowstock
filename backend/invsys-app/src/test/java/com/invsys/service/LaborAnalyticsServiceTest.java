package com.invsys.service;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LaborAnalyticsServiceTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID USER = UUID.fromString("a0000000-0000-4000-8000-000000000204");

    @Mock
    private DSLContext dsl;

    private LaborAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new LaborAnalyticsService(dsl);
    }

    @Test
    void returnsEmptyWhenNoFloorActivity() {
        when(dsl.fetch(anyString(), any(Object[].class))).thenReturn(emptyResult());

        List<LaborAnalyticsService.OperatorVelocity> rows = service.calculateOperatorVelocity(
                TENANT,
                Instant.parse("2026-07-16T00:00:00Z"),
                Instant.parse("2026-07-16T23:59:59Z"));

        assertThat(rows).isEmpty();
    }

    @Test
    void computesActiveShiftPphAndUtilizationFromWaveTasks() {
        Instant start = Instant.parse("2026-07-16T12:00:00Z");
        Instant end = Instant.parse("2026-07-16T13:00:00Z");

        Result<Record> ledgerEmpty = emptyResult();
        Result<Record> pickRows = resultOf(pickRow(USER, 10L, start, end));
        Result<Record> waveRows = resultOf(waveRow(USER, start, end));
        Result<Record> auditEmpty = emptyResult();
        Result<Record> names = resultOf(nameRow(USER, "Floor Picker"));
        Result<Record> hourly = resultOf(hourlyRow(USER, start, 10L));
        Result<Record> shipHours = emptyResult();

        when(dsl.fetch(anyString(), any(Object[].class))).thenReturn(
                ledgerEmpty, pickRows, waveRows, auditEmpty, names, hourly, shipHours);

        List<LaborAnalyticsService.OperatorVelocity> rows =
                service.calculateOperatorVelocity(TENANT, start, end.plusSeconds(1));

        assertThat(rows).hasSize(1);
        LaborAnalyticsService.OperatorVelocity op = rows.getFirst();
        assertThat(op.operatorName()).isEqualTo("Floor Picker");
        assertThat(op.totalPicks()).isEqualTo(10);
        assertThat(op.activePph()).isEqualByComparingTo("10.00");
        assertThat(op.shiftPph()).isEqualByComparingTo("10.00");
        assertThat(op.utilizationPercent()).isEqualByComparingTo("100.0");
        assertThat(op.hourlyPicks()).isNotEmpty();
    }

    @Test
    void fallsBackToLedgerShipPicksAndCapsUtilization() {
        Instant start = Instant.parse("2026-07-16T08:00:00Z");
        Instant mid = Instant.parse("2026-07-16T09:00:00Z");
        Instant end = Instant.parse("2026-07-16T12:00:00Z");

        Result<Record> ledger = resultOf(
                ledgerRow(USER, "SHIP", 20L, "20", start, end),
                ledgerRow(USER, "RECEIVE", 3L, "3", start, mid));
        Result<Record> pickEmpty = emptyResult();
        // Active wave only 1h inside a 4h shift → util 25%
        Result<Record> waveRows = resultOf(waveRow(USER, start, mid));
        Result<Record> audit = resultOf(auditRow(USER, start, end));
        Result<Record> names = resultOf(nameRow(USER, "Shipper"));
        Result<Record> hourlyEmpty = emptyResult();
        Result<Record> shipHours = resultOf(hourlyRow(USER, start, 20L));

        when(dsl.fetch(anyString(), any(Object[].class))).thenReturn(
                ledger, pickEmpty, waveRows, audit, names, hourlyEmpty, shipHours);

        List<LaborAnalyticsService.OperatorVelocity> rows =
                service.calculateOperatorVelocity(TENANT, start, end.plusSeconds(1));

        assertThat(rows).hasSize(1);
        LaborAnalyticsService.OperatorVelocity op = rows.getFirst();
        assertThat(op.totalPicks()).isEqualTo(20);
        assertThat(op.totalReceives()).isEqualTo(3);
        assertThat(op.activePph()).isEqualByComparingTo("20.00");
        assertThat(op.shiftPph()).isEqualByComparingTo("5.00");
        assertThat(op.utilizationPercent()).isEqualByComparingTo("25.0");
        assertThat(op.hourlyPicks()).isNotEmpty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Result emptyResult() {
        return DSL.using(SQLDialect.POSTGRES).newResult();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Result<Record> resultOf(Record... records) {
        DSLContext ctx = DSL.using(SQLDialect.POSTGRES);
        Result<Record> result = ctx.newResult(records[0].fields());
        for (Record r : records) {
            result.add(r);
        }
        return result;
    }

    private static Record pickRow(UUID userId, long picks, Instant first, Instant last) {
        return DSL.using(SQLDialect.POSTGRES).newRecord(
                        field("user_id", UUID.class),
                        field("pick_count", Long.class),
                        field("first_at", OffsetDateTime.class),
                        field("last_at", OffsetDateTime.class))
                .values(userId, picks, odt(first), odt(last));
    }

    private static Record ledgerRow(
            UUID userId, String type, long events, String units, Instant first, Instant last) {
        return DSL.using(SQLDialect.POSTGRES).newRecord(
                        field("user_id", UUID.class),
                        field("movement_type", String.class),
                        field("event_count", Long.class),
                        field("unit_qty", BigDecimal.class),
                        field("first_at", OffsetDateTime.class),
                        field("last_at", OffsetDateTime.class))
                .values(userId, type, events, new BigDecimal(units), odt(first), odt(last));
    }

    private static Record auditRow(UUID userId, Instant first, Instant last) {
        return DSL.using(SQLDialect.POSTGRES).newRecord(
                        field("user_id", UUID.class),
                        field("first_at", OffsetDateTime.class),
                        field("last_at", OffsetDateTime.class))
                .values(userId, odt(first), odt(last));
    }

    private static Record waveRow(UUID userId, Instant claimed, Instant ended) {
        return DSL.using(SQLDialect.POSTGRES).newRecord(
                        field("user_id", UUID.class),
                        field("claimed_at", OffsetDateTime.class),
                        field("ended_at", OffsetDateTime.class))
                .values(userId, odt(claimed), odt(ended));
    }

    private static Record nameRow(UUID userId, String name) {
        return DSL.using(SQLDialect.POSTGRES).newRecord(
                        field("id", UUID.class),
                        field("display_name", String.class))
                .values(userId, name);
    }

    private static Record hourlyRow(UUID userId, Instant hour, long picks) {
        return DSL.using(SQLDialect.POSTGRES).newRecord(
                        field("user_id", UUID.class),
                        field("hour_bucket", OffsetDateTime.class),
                        field("picks", Long.class))
                .values(userId, odt(hour), picks);
    }

    private static <T> Field<T> field(String name, Class<T> type) {
        return DSL.field(name, type);
    }

    private static OffsetDateTime odt(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
