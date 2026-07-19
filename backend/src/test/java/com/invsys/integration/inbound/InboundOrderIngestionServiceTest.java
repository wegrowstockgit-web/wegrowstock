package com.invsys.integration.inbound;

import com.invsys.common.ApiException;
import com.invsys.domain.SalesOrder;
import com.invsys.integration.channel.SyncDirection;
import com.invsys.integration.channel.SyncEntityType;
import com.invsys.integration.channel.SyncLogStatus;
import com.invsys.service.IntegrationChannelService;
import com.invsys.service.IntegrationSyncHistoryService;
import com.invsys.service.SalesOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InboundOrderIngestionServiceTest {

    @Mock ExternalOrderAdapter shopifyAdapter;
    @Mock SalesOrderService salesOrderService;
    @Mock IntegrationChannelService channelService;
    @Mock IntegrationSyncHistoryService syncHistoryService;

    private InboundOrderIngestionService service;

    @BeforeEach
    void setUp() {
        when(shopifyAdapter.supports(anyString())).thenAnswer(inv -> {
            String ch = inv.getArgument(0);
            return ch != null && "SHOPIFY".equalsIgnoreCase(ch.trim());
        });
        when(channelService.findActive(any())).thenReturn(Optional.empty());
        service = new InboundOrderIngestionService(
                List.of(shopifyAdapter), salesOrderService, channelService, syncHistoryService);
    }

    @Test
    void ingestDelegatesToMatchingAdapter_andRecordsSuccess() {
        CanonicalInboundOrder canonical = new CanonicalInboundOrder(
                "#1", ChannelSource.SHOPIFY, "a@b.com",
                CanonicalAddress.empty(), CanonicalAddress.empty(), List.of());
        SalesOrder created = new SalesOrder();
        created.setId(UUID.randomUUID());
        created.setNumber("SO-1");
        created.setStatus("CONFIRMED");
        when(shopifyAdapter.translate(eq("{}"), any())).thenReturn(canonical);
        when(salesOrderService.createFromCanonical(canonical)).thenReturn(created);

        SalesOrder result = service.ingest("SHOPIFY", "{}", Map.of("X-Test", "1"));
        assertThat(result).isSameAs(created);
        verify(salesOrderService).createFromCanonical(canonical);
        verify(syncHistoryService).record(
                isNull(),
                eq("SHOPIFY"),
                eq(SyncDirection.INBOUND),
                eq(SyncEntityType.ORDER),
                eq("#1"),
                eq(created.getId()),
                eq(SyncLogStatus.SUCCESS),
                any(),
                isNull());
    }

    @Test
    void unsupportedChannelThrows() {
        assertThatThrownBy(() -> service.ingest("AMAZON", "{}", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No adapter");
        verify(syncHistoryService).recordIsolated(
                isNull(),
                eq("AMAZON"),
                eq(SyncDirection.INBOUND),
                eq(SyncEntityType.ORDER),
                isNull(),
                eq(SyncLogStatus.FAILED),
                any(),
                anyString());
    }

    @Test
    void blankChannelThrows() {
        assertThatThrownBy(() -> service.ingest("  ", "{}", Map.of()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void registeredChannelsListsSupported() {
        assertThat(service.registeredChannels()).contains("SHOPIFY");
    }
}
