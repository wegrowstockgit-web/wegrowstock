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

        assertThat(prompt).contains("Operations Instructor");
        assertThat(prompt).contains("Localized page playbook");
        assertThat(prompt).contains("Sales Orders");
        assertThat(prompt).contains("Un-allocate releases stock");
        assertThat(prompt).containsIgnoringCase("append-only");
        assertThat(prompt).contains("/sales-orders");
        assertThat(prompt).contains("Reversal Guide");
        assertThat(prompt).contains("Downstream Impact");
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
    void formatPageContextIncludesComponentStatusesForHyperSpecificQuestions() {
        String block = SupportSystemPromptBuilder.formatPageContext(Map.of(
                "title", "Sales Orders",
                "purpose", "Allocate demand",
                "components", List.of(Map.of(
                        "name", "Sales Orders Grid",
                        "description", "Virtualized outbound order list",
                        "dataOrigin", "SalesOrderService",
                        "statuses", Map.of(
                                "ALLOCATED", "Stock is reserved and ready for picking wave.",
                                "DRAFT", "Order created but not confirmed."),
                        "columns", List.of(Map.of(
                                "name", "Status",
                                "purpose", "Lifecycle state of the sales order."))))));

        assertThat(block).contains("Components:");
        assertThat(block).contains("Sales Orders Grid");
        assertThat(block).contains("SalesOrderService");
        assertThat(block).contains("ALLOCATED");
        assertThat(block).contains("ready for picking wave");
        assertThat(block).contains("Columns:");
        assertThat(block).contains("Status");
    }
}
