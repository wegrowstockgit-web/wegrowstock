package com.invsys.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportSystemPromptBuilderTest {

    @Test
    void includesLocalizedPagePlaybookAndLedgerSafetyRule() {
        String prompt = SupportSystemPromptBuilder.build(
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                List.of(),
                Map.of(
                        "title", "Sales Orders",
                        "purpose", "Confirm and allocate demand",
                        "reversals", List.of("Un-allocate releases stock to the pool."),
                        "flow", List.of("Confirm", "Allocate"),
                        "correlations", List.of("Feeds the picking wave.")));

        assertThat(prompt).contains("Gemini 2.0 Flash");
        assertThat(prompt).contains("Growstock Inventory Co-Pilot");
        assertThat(prompt).contains("Operations Instructor");
        assertThat(prompt).contains("Localized page playbook");
        assertThat(prompt).contains("Sales Orders");
        assertThat(prompt).contains("Un-allocate releases stock");
        assertThat(prompt).contains("/sales-orders");
        assertThat(prompt).contains("Ledger Safety");
        assertThat(prompt).contains("Downstream Impact");
        assertThat(prompt).contains("START_TOUR");
        assertThat(prompt).contains("NAVIGATE");
        assertThat(prompt).contains("SPOTLIGHT");
        assertThat(prompt).contains("When guiding the user, generate actionable chips");
        assertThat(prompt).contains("/purchase-orders");
        assertThat(prompt).contains("data-tour");
        assertThat(prompt).contains("[data-tour='btn-unallocate']");
        assertThat(prompt).contains("followUpQuestions");
        assertThat(prompt).contains("actionDraft");
        assertThat(prompt).containsIgnoringCase("httpMethod");
        assertThat(prompt).containsIgnoringCase("release unallocated stock");
        assertThat(prompt).containsIgnoringCase("do NOT just write instructions");
        assertThat(prompt).contains("one click");
        assertThat(prompt).contains("checkAvailableToPromise");
        assertThat(prompt).contains("checkOrderStatus");
        assertThat(prompt).contains("getLedgerHistorySummary");
        assertThat(prompt).contains(
                "Whenever the user query mentions a specific Sales Order number or SKU, invoke your");
        assertThat(prompt).contains("diagnostic read tools first");
        assertThat(prompt).contains("Never invent or guess stock numbers or order states");
        assertThat(prompt).contains("never expose Java class names");
        assertThat(prompt).contains("ABSOLUTE LANGUAGE BAN");
        assertThat(prompt).containsIgnoringCase("NEVER mention API");
        assertThat(prompt.toLowerCase()).doesNotContain("inventory_ledger");
    }

    @Test
    void telemetryBlockTranslatesOperationalBlocker() {
        String prompt = SupportSystemPromptBuilder.build(
                List.of("WAREHOUSE_MANAGER"),
                "/inventory/transfer",
                List.of(),
                Map.of("title", "Transfer"),
                Map.of(
                        "lastHttpErrorStatus", 409,
                        "lastHttpErrorMessage", "BIN_LOCKED_BY_CYCLE_COUNT",
                        "trace_id", "abc123trace"));

        assertThat(prompt).contains("Recent operational blocker");
        assertThat(prompt).containsIgnoringCase("never mention HTTP");
        assertThat(prompt).contains("abc123trace");
    }

    @Test
    void includesLivePageStateSnapshot() {
        String prompt = SupportSystemPromptBuilder.build(
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders?status=BACKORDERED",
                List.of(),
                Map.of("title", "Sales Orders"),
                Map.of(
                        "activeWarehouseId", "wh-1",
                        "activeFilter", "status=BACKORDERED",
                        "networkState", "online",
                        "selectedEntity", "SO-9"));

        assertThat(prompt).contains("Live page state snapshot");
        assertThat(prompt).contains("wh-1");
        assertThat(prompt).contains("status=BACKORDERED");
        assertThat(prompt).contains("SO-9");
    }

    @Test
    void formatPageContextHandlesEmptyMaps() {
        assertThat(SupportSystemPromptBuilder.formatPageContext(Map.of())).isEmpty();
        assertThat(SupportSystemPromptBuilder.formatPageContext(null)).isEmpty();
    }

    @Test
    void formatPageContextPrefersHumanFacingFields() {
        String block = SupportSystemPromptBuilder.formatPageContext(Map.of(
                "title", "Sales Orders",
                "purpose", "Confirm and allocate demand",
                "dataOrigin", "Orders entered by the office or connected storefronts.",
                "whoCanUse", List.of("Warehouse Managers", "Administrators"),
                "stepByStepFlow", List.of("Confirm", "Allocate"),
                "howToUndo", List.of("Un-allocate releases reserved stock."),
                "correlations", List.of("Pickers see released waves on handhelds.")));

        assertThat(block).contains("Where this comes from:");
        assertThat(block).contains("Who can use this page:");
        assertThat(block).contains("Warehouse Managers");
        assertThat(block).contains("Step-by-step:");
        assertThat(block).contains("How to undo:");
        assertThat(block).contains("Who else this affects:");
        assertThat(block).doesNotContain("Allowed roles");
    }

    @Test
    void includesRecentBreadcrumbsAndTemporalMemoryInstructions() {
        String prompt = SupportSystemPromptBuilder.build(
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                List.of(),
                Map.of("title", "Sales Orders"),
                Map.of(
                        "recentBreadcrumbs", List.of(
                                Map.of(
                                        "actionType", "CLICK",
                                        "elementLabel", "Confirm Receiving",
                                        "errorMessage", ""),
                                Map.of(
                                        "actionType", "TOAST_ERROR",
                                        "elementLabel", "Save Settings",
                                        "errorMessage", "Bin is locked by cycle count"),
                                Map.of(
                                        "actionType", "SCAN_REJECTED",
                                        "elementLabel", "Scan SKU",
                                        "errorMessage", "Unknown barcode"))));

        assertThat(prompt).contains("recentBreadcrumbs");
        assertThat(prompt).contains("TOAST_ERROR or SCAN_REJECTED");
        assertThat(prompt).containsIgnoringCase("exact button or field label");
        assertThat(prompt).contains("Confirm Receiving");
        assertThat(prompt).contains("TOAST_ERROR");
        assertThat(prompt).contains("SCAN_REJECTED");
        assertThat(prompt).contains("Bin is locked by cycle count");
        assertThat(prompt).containsIgnoringCase("damaged barcode or shipping label");
        assertThat(prompt).containsIgnoringCase("cross-reference the extracted data");
        assertThat(prompt).contains("1…N");
    }

    @Test
    void formatPageContextIncludesComponentStatusesForHyperSpecificQuestions() {
        String block = SupportSystemPromptBuilder.formatPageContext(Map.of(
                "title", "Sales Orders",
                "purpose", "Allocate demand",
                "components", List.of(Map.of(
                        "name", "Sales Orders Grid",
                        "description", "Virtualized outbound order list",
                        "dataOrigin", "Sales orders entered by your office team or connected storefronts.",
                        "statuses", Map.of(
                                "ALLOCATED", "Stock is reserved and ready for picking wave.",
                                "DRAFT", "Order created but not confirmed."),
                        "columns", List.of(Map.of(
                                "name", "Status",
                                "purpose", "Lifecycle state of the sales order."))))));

        assertThat(block).contains("Components:");
        assertThat(block).contains("Sales Orders Grid");
        assertThat(block).contains("where this comes from:");
        assertThat(block).contains("office team or connected storefronts");
        assertThat(block).contains("ALLOCATED");
        assertThat(block).contains("ready for picking wave");
        assertThat(block).contains("Columns:");
        assertThat(block).contains("Status");
        assertThat(block).doesNotContain("SalesOrderService");
    }
}
