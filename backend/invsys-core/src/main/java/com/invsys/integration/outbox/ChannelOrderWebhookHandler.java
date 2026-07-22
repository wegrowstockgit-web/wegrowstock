package com.invsys.integration.outbox;

import com.invsys.core.common.MdcSupport;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.domain.WebhookEvent;
import com.invsys.integration.channel.SyncDirection;
import com.invsys.integration.channel.SyncEntityType;
import com.invsys.integration.channel.SyncLogStatus;
import com.invsys.integration.inbound.CanonicalInboundOrder;
import com.invsys.integration.inbound.ShopifyOrderAdapter;
import com.invsys.repository.WebhookEventRepository;
import com.invsys.service.IntegrationChannelService;
import com.invsys.service.IntegrationSyncHistoryService;
import com.invsys.modules.sales.service.SalesOrderService;
import com.invsys.integration.channel.IntegrationChannelType;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class ChannelOrderWebhookHandler {

    private final WebhookEventRepository webhookEventRepository;
    private final ShopifyOrderAdapter shopifyOrderAdapter;
    private final SalesOrderService salesOrderService;
    private final ObjectMapper objectMapper;
    private final IntegrationChannelService channelService;
    private final IntegrationSyncHistoryService syncHistoryService;

    public ChannelOrderWebhookHandler(WebhookEventRepository webhookEventRepository,
                                      ShopifyOrderAdapter shopifyOrderAdapter,
                                      SalesOrderService salesOrderService,
                                      ObjectMapper objectMapper,
                                      IntegrationChannelService channelService,
                                      IntegrationSyncHistoryService syncHistoryService) {
        this.webhookEventRepository = webhookEventRepository;
        this.shopifyOrderAdapter = shopifyOrderAdapter;
        this.salesOrderService = salesOrderService;
        this.objectMapper = objectMapper;
        this.channelService = channelService;
        this.syncHistoryService = syncHistoryService;
    }

    @Async("virtualThreadExecutor")
    public void processAsync(UUID eventId) {
        webhookEventRepository.findById(eventId).ifPresent(this::process);
    }

    @Transactional
    public void process(WebhookEvent event) {
        if (event.getProcessedAt() != null || event.getTenantId() == null) {
            return;
        }
        if (!"SHOPIFY".equals(event.getSource())) {
            return;
        }
        MdcSupport.run(
                event.getTenantId(),
                MdcSupport.backgroundRequestId("channel-webhook", event.getId()),
                null,
                () -> {
                    TenantContext.setTenantId(event.getTenantId());
                    try {
                        processShopifyOrder(event);
                    } catch (Exception e) {
                        event.setError(e.getMessage());
                        webhookEventRepository.save(event);
                        try {
                            syncHistoryService.recordIsolated(
                                    channelService.findActive(IntegrationChannelType.SHOPIFY).orElse(null),
                                    "SHOPIFY",
                                    SyncDirection.INBOUND,
                                    SyncEntityType.ORDER,
                                    null,
                                    SyncLogStatus.FAILED,
                                    Map.of("webhookEventId", event.getId().toString()),
                                    e.getMessage());
                        } catch (Exception ignored) {
                            // best-effort failure log
                        }
                    } finally {
                        TenantContext.clear();
                    }
                    return null;
                });
    }

    private void processShopifyOrder(WebhookEvent event) {
        Map<String, Object> payload = event.getPayload();
        String topic = (String) payload.getOrDefault("topic", "");
        if (!topic.contains("orders/")) {
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
            return;
        }

        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Shopify webhook payload", ex);
        }

        CanonicalInboundOrder canonical = shopifyOrderAdapter.translate(
                rawPayload,
                Map.of(ShopifyOrderAdapter.HEADER_INTERNAL_TRUSTED, "true"));
        SalesOrder order = salesOrderService.createFromCanonical(canonical);
        SyncLogStatus status = "NEEDS_REVIEW".equals(order.getStatus())
                ? SyncLogStatus.WARNING
                : SyncLogStatus.SUCCESS;
        syncHistoryService.record(
                channelService.findActive(IntegrationChannelType.SHOPIFY).orElse(null),
                "SHOPIFY",
                SyncDirection.INBOUND,
                SyncEntityType.ORDER,
                canonical.externalOrderRef(),
                order.getId(),
                status,
                Map.of(
                        "orderId", order.getId().toString(),
                        "orderNumber", order.getNumber(),
                        "webhookEventId", event.getId().toString()),
                null);
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);
    }
}
