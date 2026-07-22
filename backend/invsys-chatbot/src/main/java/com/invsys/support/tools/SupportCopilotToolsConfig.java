package com.invsys.support.tools;

import com.invsys.support.tools.SupportCopilotToolModels.AtpRequest;
import com.invsys.support.tools.SupportCopilotToolModels.AtpResponse;
import com.invsys.support.tools.SupportCopilotToolModels.LedgerHistoryRequest;
import com.invsys.support.tools.SupportCopilotToolModels.LedgerHistoryResponse;
import com.invsys.support.tools.SupportCopilotToolModels.OrderStatusRequest;
import com.invsys.support.tools.SupportCopilotToolModels.OrderStatusResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.support.SupportChatService;

/**
 * Registers read-only multi-tenant CQRS tools for the Support Copilot ChatClient.
 *
 * <p><b>Security:</b> every tool implementation resolves the tenant exclusively via
 * {@link com.invsys.core.tenancy.TenantContext#getTenantId()} /
 * {@link com.invsys.core.tenancy.TenantContext#requireTenantId()}.
 * Request DTOs must never accept a tenantId argument from the LLM.
 */
@Configuration
public class SupportCopilotToolsConfig {

    @Bean
    @Description(
            "Queries real-time Available-to-Promise (ATP) inventory levels and incoming POs "
                    + "for a specific SKU in a warehouse.")
    public Function<AtpRequest, AtpResponse> checkAvailableToPromise(SupportCopilotReadService readService) {
        return readService::checkAvailableToPromise;
    }

    @Bean
    @Description(
            "Looks up the current operational status of a Sales Order. Use this whenever the user "
                    + "asks about an order by its number (e.g., SO-123). Returns allocation holds, "
                    + "credit holds, or missing SKU details when present.")
    public Function<OrderStatusRequest, OrderStatusResponse> checkOrderStatus(SupportCopilotReadService readService) {
        // Delegates to SalesOrderRepository via SupportCopilotReadService (tenant-scoped; never findAll).
        return readService::checkOrderStatus;
    }

    @Bean
    @Description(
            "Looks up recent stock ledger movement entries for a variant UUID / SKU to explain why "
                    + "inventory was adjusted, moved, or received.")
    public Function<LedgerHistoryRequest, LedgerHistoryResponse> getLedgerHistorySummary(
            SupportCopilotReadService readService
    ) {
        // Delegates to InventoryLedgerRepository via SupportCopilotReadService + InventoryService.
        return readService::getLedgerHistorySummary;
    }

    @Bean
    public ToolCallback checkAvailableToPromiseTool(Function<AtpRequest, AtpResponse> checkAvailableToPromise) {
        return FunctionToolCallback.builder("checkAvailableToPromise", checkAvailableToPromise)
                .description(
                        "Queries real-time Available-to-Promise (ATP) inventory levels and incoming POs "
                                + "for a specific SKU in a warehouse. Do not pass tenantId — "
                                + "the platform supplies the tenant from TenantContext.")
                .inputType(AtpRequest.class)
                .build();
    }

    @Bean
    public ToolCallback checkOrderStatusTool(Function<OrderStatusRequest, OrderStatusResponse> checkOrderStatus) {
        return FunctionToolCallback.builder("checkOrderStatus", checkOrderStatus)
                .description(
                        "Looks up the current operational status of a Sales Order. Use this whenever "
                                + "the user asks about an order by its number (e.g., SO-123). "
                                + "Do not pass tenantId — the platform supplies the tenant from TenantContext.")
                .inputType(OrderStatusRequest.class)
                .build();
    }

    @Bean
    public ToolCallback getLedgerHistorySummaryTool(
            Function<LedgerHistoryRequest, LedgerHistoryResponse> getLedgerHistorySummary
    ) {
        return FunctionToolCallback.builder("getLedgerHistorySummary", getLedgerHistorySummary)
                .description(
                        "Looks up recent stock ledger movement entries for a variant to explain why "
                                + "inventory was adjusted, moved, or received. Do not pass tenantId — "
                                + "the platform supplies the tenant from TenantContext.")
                .inputType(LedgerHistoryRequest.class)
                .build();
    }

    /**
     * Aggregation bound onto ChatClient via SupportChatService
     * {@code defaultToolNames(...)} / {@code defaultToolCallbacks(...)}
     * (Spring AI 1.1; formerly {@code defaultFunctions}).
     */
    @Bean
    public List<ToolCallback> supportCopilotReadToolCallbacks(
            ToolCallback checkAvailableToPromiseTool,
            ToolCallback checkOrderStatusTool,
            ToolCallback getLedgerHistorySummaryTool
    ) {
        return List.of(checkAvailableToPromiseTool, checkOrderStatusTool, getLedgerHistorySummaryTool);
    }
}
