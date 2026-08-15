package com.invsys.admin.service;

import com.invsys.domain.subscription.AppModule;
import com.invsys.domain.subscription.CommercialTier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminReportingService {

    private final JdbcTemplate jdbc;

    public AdminReportingService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
    }

    public CommercialReport commercialReport() {
        List<TierDistributionRow> tierDistribution = jdbc.query(
                """
                SELECT COALESCE(s.tier, 'BASIC') AS tier, COUNT(*) AS count
                FROM tenants t
                LEFT JOIN tenant_subscriptions s ON s.tenant_id = t.id
                GROUP BY COALESCE(s.tier, 'BASIC')
                ORDER BY tier
                """,
                (rs, rowNum) -> new TierDistributionRow(
                        CommercialTier.fromString(rs.getString("tier")),
                        rs.getInt("count")));

        List<ModuleAdoptionRow> moduleAdoption = jdbc.query(
                """
                SELECT module_code, COUNT(DISTINCT tenant_id) AS tenant_count
                FROM (
                    SELECT s.tenant_id, jsonb_array_elements_text(s.enabled_modules) AS module_code
                    FROM tenant_subscriptions s
                    UNION ALL
                    SELECT t.id, 'CORE'
                    FROM tenants t
                    LEFT JOIN tenant_subscriptions s ON s.tenant_id = t.id
                    WHERE s.tenant_id IS NULL
                ) modules
                GROUP BY module_code
                ORDER BY tenant_count DESC, module_code
                """,
                (rs, rowNum) -> new ModuleAdoptionRow(
                        AppModule.valueOf(rs.getString("module_code")),
                        rs.getInt("tenant_count")));

        List<GmvRow> gmvByMonth = jdbc.query(
                """
                SELECT to_char(date_trunc('month', so.created_at), 'YYYY-MM') AS month,
                       COALESCE(SUM(sol.qty_ordered * sol.unit_price), 0) AS gmv
                FROM sales_orders so
                JOIN sales_order_lines sol
                  ON sol.sales_order_id = so.id AND sol.tenant_id = so.tenant_id
                GROUP BY date_trunc('month', so.created_at)
                ORDER BY month
                """,
                (rs, rowNum) -> new GmvRow(
                        rs.getString("month"),
                        rs.getBigDecimal("gmv")));

        return new CommercialReport(tierDistribution, moduleAdoption, gmvByMonth);
    }

    public HealthReport healthReport() {
        List<WebhookFailureRow> webhookFailures = jdbc.query(
                """
                SELECT t.slug AS tenant_slug,
                       isl.system AS endpoint,
                       COUNT(*) AS failures_24h,
                       MAX(COALESCE(isl.last_error, isl.error_message, '')) AS last_error
                FROM integration_sync_logs isl
                JOIN tenants t ON t.id = isl.tenant_id
                WHERE isl.status = 'FAILED'
                  AND isl.created_at >= NOW() - INTERVAL '24 hours'
                GROUP BY t.slug, isl.system
                ORDER BY failures_24h DESC, t.slug, isl.system
                """,
                (rs, rowNum) -> new WebhookFailureRow(
                        rs.getString("tenant_slug"),
                        rs.getString("endpoint"),
                        rs.getInt("failures_24h"),
                        rs.getString("last_error")));

        List<RateLimitRow> rateLimitHits = jdbc.query(
                """
                SELECT t.slug AS tenant_slug,
                       oe.event_type AS route,
                       COUNT(*) AS hits_24h
                FROM outbox_events oe
                JOIN tenants t ON t.id = oe.tenant_id
                WHERE oe.status = 'FAILED'
                  AND oe.last_error ILIKE '%rate%'
                  AND oe.created_at >= NOW() - INTERVAL '24 hours'
                GROUP BY t.slug, oe.event_type
                ORDER BY hits_24h DESC, t.slug, oe.event_type
                """,
                (rs, rowNum) -> new RateLimitRow(
                        rs.getString("tenant_slug"),
                        rs.getString("route"),
                        rs.getInt("hits_24h")));

        List<LedgerGrowthRow> ledgerGrowth = jdbc.query(
                """
                SELECT t.slug AS tenant_slug,
                       COUNT(*) FILTER (WHERE il.created_at >= NOW() - INTERVAL '30 days') AS entries_30d,
                       COUNT(*) AS total_entries
                FROM tenants t
                LEFT JOIN inventory_ledger il ON il.tenant_id = t.id
                GROUP BY t.slug
                HAVING COUNT(il.id) > 0
                ORDER BY entries_30d DESC, t.slug
                """,
                (rs, rowNum) -> new LedgerGrowthRow(
                        rs.getString("tenant_slug"),
                        rs.getInt("entries_30d"),
                        rs.getInt("total_entries")));

        return new HealthReport(webhookFailures, rateLimitHits, ledgerGrowth);
    }

    public record TierDistributionRow(CommercialTier tier, int count) {
    }

    public record ModuleAdoptionRow(AppModule module, int tenantCount) {
    }

    public record GmvRow(String month, BigDecimal gmv) {
    }

    public record CommercialReport(
            List<TierDistributionRow> tierDistribution,
            List<ModuleAdoptionRow> moduleAdoption,
            List<GmvRow> gmvByMonth
    ) {
    }

    public record WebhookFailureRow(String tenantSlug, String endpoint, int failures24h, String lastError) {
    }

    public record RateLimitRow(String tenantSlug, String route, int hits24h) {
    }

    public record LedgerGrowthRow(String tenantSlug, int entries30d, int totalEntries) {
    }

    public record HealthReport(
            List<WebhookFailureRow> webhookFailures,
            List<RateLimitRow> rateLimitHits,
            List<LedgerGrowthRow> ledgerGrowth
    ) {
    }
}
