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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class ReportsController {

    private final ReconciliationService reconciliationService;
    private final ReportingAnalyticsService reportingAnalyticsService;

    public ReportsController(ReconciliationService reconciliationService,
                             ReportingAnalyticsService reportingAnalyticsService) {
        this.reconciliationService = reconciliationService;
        this.reportingAnalyticsService = reportingAnalyticsService;
    }

    @GetMapping("/reconciliation")
    public ReconciliationService.ReconciliationReport reconciliation() {
        return reconciliationService.report();
    }

    @GetMapping("/inventory-valuation")
    public InventoryValuationReport inventoryValuation() {
        return reportingAnalyticsService.inventoryValuation();
    }

    @GetMapping("/stock-turnover")
    public StockTurnoverReport stockTurnover(@RequestParam(defaultValue = "30") int periodDays) {
        return reportingAnalyticsService.stockTurnover(periodDays);
    }

    @GetMapping("/cogs-ledger")
    public CogsLedgerReport cogsLedger() {
        return reportingAnalyticsService.cogsLedger();
    }

    @GetMapping("/profit-margin")
    public ProfitMarginReport profitMargin(@RequestParam(defaultValue = "90") int periodDays) {
        return reportingAnalyticsService.profitMargin(periodDays);
    }

    @GetMapping("/sales-performance")
    public SalesPerformanceReport salesPerformance(@RequestParam(defaultValue = "90") int periodDays) {
        return reportingAnalyticsService.salesPerformance(periodDays);
    }

    @GetMapping("/fulfillment-summary")
    public FulfillmentSummaryReport fulfillmentSummary(@RequestParam(defaultValue = "30") int periodDays) {
        return reportingAnalyticsService.fulfillmentSummary(periodDays);
    }

    @GetMapping("/purchase-spend")
    public PurchaseSpendReport purchaseSpend(@RequestParam(defaultValue = "90") int periodDays) {
        return reportingAnalyticsService.purchaseSpend(periodDays);
    }

    @GetMapping("/returns-analysis")
    public ReturnsAnalysisReport returnsAnalysis(@RequestParam(defaultValue = "90") int periodDays) {
        return reportingAnalyticsService.returnsAnalysis(periodDays);
    }
}
