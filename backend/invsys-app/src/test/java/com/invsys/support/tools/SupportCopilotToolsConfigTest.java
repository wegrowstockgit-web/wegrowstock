package com.invsys.support.tools;

import com.invsys.support.tools.SupportCopilotToolModels.AtpRequest;
import com.invsys.support.tools.SupportCopilotToolModels.AtpResponse;
import com.invsys.support.tools.SupportCopilotToolModels.LedgerHistoryRequest;
import com.invsys.support.tools.SupportCopilotToolModels.LedgerHistoryResponse;
import com.invsys.support.tools.SupportCopilotToolModels.OrderStatusRequest;
import com.invsys.support.tools.SupportCopilotToolModels.OrderStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportCopilotToolsConfigTest {

    @Mock SupportCopilotReadService readService;

    SupportCopilotToolsConfig config = new SupportCopilotToolsConfig();

    @Test
    void registersNamedFunctionBeansDelegatingToReadService() {
        when(readService.checkAvailableToPromise(any()))
                .thenReturn(new AtpResponse("SKU-1", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, null));
        when(readService.checkOrderStatus(any()))
                .thenReturn(new OrderStatusResponse("SO-1", "CONFIRMED", null, null));
        when(readService.getLedgerHistorySummary(any()))
                .thenReturn(new LedgerHistoryResponse("SKU-1", List.of()));

        Function<AtpRequest, AtpResponse> atp = config.checkAvailableToPromise(readService);
        Function<OrderStatusRequest, OrderStatusResponse> order = config.checkOrderStatus(readService);
        Function<LedgerHistoryRequest, LedgerHistoryResponse> ledger =
                config.getLedgerHistorySummary(readService);

        assertThat(atp.apply(new AtpRequest("SKU-1", null)).sku()).isEqualTo("SKU-1");
        assertThat(order.apply(new OrderStatusRequest("SO-1")).status()).isEqualTo("CONFIRMED");
        assertThat(ledger.apply(new LedgerHistoryRequest("SKU-1", 5)).sku()).isEqualTo("SKU-1");

        verify(readService).checkAvailableToPromise(any());
        verify(readService).checkOrderStatus(any());
        verify(readService).getLedgerHistorySummary(any());
    }

    @Test
    void toolCallbacksExposeStableNamesForChatClientBinding() {
        Function<AtpRequest, AtpResponse> atp = req ->
                new AtpResponse(req.sku(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        Function<OrderStatusRequest, OrderStatusResponse> order = req ->
                new OrderStatusResponse(req.orderNumber(), "OPEN", null, null);
        Function<LedgerHistoryRequest, LedgerHistoryResponse> ledger = req ->
                new LedgerHistoryResponse(req.sku(), List.of());

        ToolCallback atpTool = config.checkAvailableToPromiseTool(atp);
        ToolCallback orderTool = config.checkOrderStatusTool(order);
        ToolCallback ledgerTool = config.getLedgerHistorySummaryTool(ledger);
        List<ToolCallback> all = config.supportCopilotReadToolCallbacks(atpTool, orderTool, ledgerTool);

        assertThat(all).extracting(ToolCallback::getToolDefinition)
                .extracting(def -> def.name())
                .containsExactly("checkAvailableToPromise", "checkOrderStatus", "getLedgerHistorySummary");
        assertThat(atpTool.getToolDefinition().description())
                .contains("Available-to-Promise")
                .containsIgnoringCase("tenantId");
        assertThat(orderTool.getToolDefinition().description())
                .contains("Sales Order")
                .contains("SO-123");
        assertThat(ledgerTool.getToolDefinition().description())
                .contains("ledger movement");
        assertThat(AtpRequest.class.getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("tenantId");
        assertThat(OrderStatusRequest.class.getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("tenantId");
        assertThat(LedgerHistoryRequest.class.getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("tenantId");
    }
}
