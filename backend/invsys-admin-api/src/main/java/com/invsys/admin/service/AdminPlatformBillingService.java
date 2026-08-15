package com.invsys.admin.service;

import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Platform billing overview. JDBC estimates only — Stripe secret keys can wire later when a Stripe SDK is added.
 * Env {@code STRIPE_SECRET_KEY} is intentionally ignored here (no Stripe dependency in admin-api).
 */
@Service
public class AdminPlatformBillingService {

    private static final Map<String, BigDecimal> TIER_MRR = Map.of(
            "BASIC", BigDecimal.valueOf(99),
            "INTERMEDIATE", BigDecimal.valueOf(299),
            "ENTERPRISE", BigDecimal.valueOf(799)
    );

    private final JdbcTemplate jdbc;
    private final BootstrapJdbc bootstrapJdbc;

    public AdminPlatformBillingService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
                                       BootstrapJdbc bootstrapJdbc) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
        this.bootstrapJdbc = bootstrapJdbc;
    }

    public BillingOverview overview() {
        List<BootstrapJdbc.TenantWithSubscriptionRow> tenants = bootstrapJdbc.listTenantsWithSubscriptions();
        BigDecimal mrr = BigDecimal.ZERO;
        List<TenantBillingCard> cards = new ArrayList<>();

        for (BootstrapJdbc.TenantWithSubscriptionRow t : tenants) {
            boolean active = "ACTIVE".equalsIgnoreCase(t.status());
            if (active) {
                mrr = mrr.add(TIER_MRR.getOrDefault(t.tier() == null ? "BASIC" : t.tier().toUpperCase(), BigDecimal.valueOf(99)));
            }
            long shippedLines = countShippedUsage(t.tenantId());
            String cardStatus = active ? "PAID" : "PAST_DUE";
            cards.add(new TenantBillingCard(
                    t.tenantId(),
                    t.slug(),
                    t.tier(),
                    cardStatus,
                    shippedLines,
                    usageLimitsForTier(t.tier())));
        }

        return new BillingOverview(mrr, cards);
    }

    private long countShippedUsage(java.util.UUID tenantId) {
        try {
            Long fromLines = jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM sales_order_lines sol
                    JOIN sales_orders so ON so.id = sol.sales_order_id AND so.tenant_id = sol.tenant_id
                    WHERE sol.tenant_id = ?
                      AND (so.status IN ('SHIPPED', 'FULFILLED', 'CLOSED') OR sol.qty_shipped > 0)
                    """,
                    Long.class,
                    tenantId);
            if (fromLines != null && fromLines > 0) {
                return fromLines;
            }
        } catch (Exception ignored) {
            // table/columns may differ — fall through
        }
        try {
            Long fromShipments = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shipments WHERE tenant_id = ?",
                    Long.class,
                    tenantId);
            return fromShipments == null ? 0L : fromShipments;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static UsageLimits usageLimitsForTier(String tier) {
        String t = tier == null ? "BASIC" : tier.toUpperCase();
        return switch (t) {
            case "ENTERPRISE" -> new UsageLimits(1_000_000, 500);
            case "INTERMEDIATE" -> new UsageLimits(100_000, 50);
            default -> new UsageLimits(10_000, 10);
        };
    }

    public record UsageLimits(long shippedLinesPerMonth, int warehouses) {
    }

    public record TenantBillingCard(
            java.util.UUID tenantId,
            String slug,
            String tier,
            String cardStatus,
            long shippedLinesUsage,
            UsageLimits usageLimits
    ) {
    }

    public record BillingOverview(BigDecimal estimatedMrr, List<TenantBillingCard> tenants) {
    }
}
