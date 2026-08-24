package com.invsys.api;

import com.invsys.core.common.exception.ReportDateRangeException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Caps analytics windows so report SQL cannot scan unbounded history.
 */
public final class ReportDateRange {

    public static final int STANDARD_MAX_DAYS = 90;
    public static final int HIGH_VOLUME_MAX_DAYS = 31;

    public record Window(int periodDays, LocalDate startDate, LocalDate endDate) {
    }

    private ReportDateRange() {
    }

    public static Window resolve(
            LocalDate startDate,
            LocalDate endDate,
            Integer periodDays,
            int defaultDays,
            int maxDays) {
        LocalDate end = endDate != null ? endDate : LocalDate.now(ZoneOffset.UTC);
        LocalDate start;
        if (startDate != null) {
            start = startDate;
        } else if (periodDays != null) {
            start = end.minusDays(Math.max(periodDays, 0));
        } else {
            start = end.minusDays(defaultDays);
        }
        if (start.isAfter(end)) {
            throw new ReportDateRangeException("Report start date must be on or before the end date.");
        }
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > maxDays) {
            throw new ReportDateRangeException(
                    "Report date range cannot exceed " + maxDays + " days. Narrow the search.");
        }
        if (periodDays != null && periodDays > maxDays) {
            throw new ReportDateRangeException(
                    "Report date range cannot exceed " + maxDays + " days. Narrow the search.");
        }
        int resolvedDays =
                startDate != null && endDate != null
                        ? (int) days
                        : (periodDays != null ? periodDays : (int) days);
        if (resolvedDays < 1) {
            resolvedDays = 1;
        }
        return new Window(resolvedDays, start, end);
    }
}
