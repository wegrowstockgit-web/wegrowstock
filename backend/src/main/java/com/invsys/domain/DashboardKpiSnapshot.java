package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "dashboard_kpi_snapshots")
public class DashboardKpiSnapshot extends TenantScopedEntity {

    @Column(name = "stock_value", nullable = false)
    private BigDecimal stockValue = BigDecimal.ZERO;

    @Column(nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "low_stock_count", nullable = false)
    private long lowStockCount;

    @Column(name = "open_orders_count", nullable = false)
    private long openOrdersCount;

    @Column(name = "unpaid_invoices_count", nullable = false)
    private long unpaidInvoicesCount;

    @Column(name = "source_event_type", length = 80)
    private String sourceEventType;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt = Instant.now();

    public BigDecimal getStockValue() {
        return stockValue;
    }

    public void setStockValue(BigDecimal stockValue) {
        this.stockValue = stockValue;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(long lowStockCount) {
        this.lowStockCount = lowStockCount;
    }

    public long getOpenOrdersCount() {
        return openOrdersCount;
    }

    public void setOpenOrdersCount(long openOrdersCount) {
        this.openOrdersCount = openOrdersCount;
    }

    public long getUnpaidInvoicesCount() {
        return unpaidInvoicesCount;
    }

    public void setUnpaidInvoicesCount(long unpaidInvoicesCount) {
        this.unpaidInvoicesCount = unpaidInvoicesCount;
    }

    public String getSourceEventType() {
        return sourceEventType;
    }

    public void setSourceEventType(String sourceEventType) {
        this.sourceEventType = sourceEventType;
    }

    public Instant getRefreshedAt() {
        return refreshedAt;
    }

    public void setRefreshedAt(Instant refreshedAt) {
        this.refreshedAt = refreshedAt;
    }
}
