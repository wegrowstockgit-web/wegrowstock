package com.invsys.api;

import com.invsys.api.dto.CogsLedgerReport;
import com.invsys.api.dto.FulfillmentSummaryReport;
import com.invsys.api.dto.InventoryValuationReport;
import com.invsys.api.dto.ProfitMarginReport;
import com.invsys.api.dto.PurchaseSpendReport;
import com.invsys.api.dto.ReturnsAnalysisReport;
import com.invsys.api.dto.SalesPerformanceReport;
import com.invsys.api.dto.StockTurnoverReport;
import com.invsys.service.ReconciliationService;
import com.invsys.service.ReportingAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Tenant analytics boards. Financial extracts require OWNER / ADMIN / FINANCE_ADMIN.
 * Operational boards also admit WAREHOUSE_MANAGER. Date windows are capped by
 * {@link ReportDateRange} (400 when the gap is too wide).
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {

    private final ReconciliationService reconciliationService;
    private final ReportingAnalyticsService reportingAnalyticsService;

    public ReportsController(ReconciliationService reconciliationService,
                             ReportingAnalyticsService reportingAnalyticsService) {
        this.reconciliationService = reconciliationService;
        this.reportingAnalyticsService = reportingAnalyticsService;
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ReconciliationService.ReconciliationReport reconciliation() {
        return reconciliationService.report();
    }

    @GetMapping("/inventory-valuation")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public InventoryValuationReport inventoryValuation() {
        return reportingAnalyticsService.inventoryValuation();
    }

    @GetMapping("/stock-turnover")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public StockTurnoverReport stockTurnover(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer periodDays) {
        int days = ReportDateRange.resolve(
                startDate, endDate, periodDays, 30, ReportDateRange.STANDARD_MAX_DAYS).periodDays();
        return reportingAnalyticsService.stockTurnover(days);
    }

    @GetMapping("/cogs-ledger")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public CogsLedgerReport cogsLedger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer periodDays) {
        int days = ReportDateRange.resolve(
                startDate, endDate, periodDays, 31, ReportDateRange.HIGH_VOLUME_MAX_DAYS).periodDays();
        return reportingAnalyticsService.cogsLedger(days);
    }

    @GetMapping("/profit-margin")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public ProfitMarginReport profitMargin(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer periodDays) {
        int days = ReportDateRange.resolve(
                startDate, endDate, periodDays, 90, ReportDateRange.STANDARD_MAX_DAYS).periodDays();
        return reportingAnalyticsService.profitMargin(days);
    }

    @GetMapping("/sales-performance")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public SalesPerformanceReport salesPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer periodDays) {
        int days = ReportDateRange.resolve(
                startDate, endDate, periodDays, 90, ReportDateRange.STANDARD_MAX_DAYS).periodDays();
        return reportingAnalyticsService.salesPerformance(days);
    }

    @GetMapping("/fulfillment-summary")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public FulfillmentSummaryReport fulfillmentSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer periodDays) {
        int days = ReportDateRange.resolve(
                startDate, endDate, periodDays, 30, ReportDateRange.HIGH_VOLUME_MAX_DAYS).periodDays();
        return reportingAnalyticsService.fulfillmentSummary(days);
    }

    @GetMapping("/purchase-spend")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public PurchaseSpendReport purchaseSpend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer periodDays) {
        int days = ReportDateRange.resolve(
                startDate, endDate, periodDays, 90, ReportDateRange.STANDARD_MAX_DAYS).periodDays();
        return reportingAnalyticsService.purchaseSpend(days);
    }

    @GetMapping("/returns-analysis")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN','WAREHOUSE_MANAGER')")
    public ReturnsAnalysisReport returnsAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer periodDays) {
        int days = ReportDateRange.resolve(
                startDate, endDate, periodDays, 90, ReportDateRange.STANDARD_MAX_DAYS).periodDays();
        return reportingAnalyticsService.returnsAnalysis(days);
    }
}
