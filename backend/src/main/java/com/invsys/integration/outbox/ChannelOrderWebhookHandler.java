package com.invsys.integration.outbox;

import com.invsys.domain.Customer;
import com.invsys.common.MdcSupport;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.WebhookEvent;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.WebhookEventRepository;
import com.invsys.service.DocumentSequenceService;
import com.invsys.service.SoftKitExplosionService;
import com.invsys.tenancy.TenantContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ChannelOrderWebhookHandler {

    private final WebhookEventRepository webhookEventRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final ProductVariantRepository variantRepository;
    private final SoftKitExplosionService softKitExplosionService;
    private final CustomerRepository customerRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final DocumentSequenceService sequenceService;

    public ChannelOrderWebhookHandler(WebhookEventRepository webhookEventRepository,
                                      SalesOrderRepository salesOrderRepository,
                                      SalesOrderLineRepository salesOrderLineRepository,
                                      ProductVariantRepository variantRepository,
                                      SoftKitExplosionService softKitExplosionService,
                                      CustomerRepository customerRepository,
                                      IntegrationSyncLogRepository syncLogRepository,
                                      DocumentSequenceService sequenceService) {
        this.webhookEventRepository = webhookEventRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.variantRepository = variantRepository;
        this.softKitExplosionService = softKitExplosionService;
        this.customerRepository = customerRepository;
        this.syncLogRepository = syncLogRepository;
        this.sequenceService = sequenceService;
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

            @SuppressWarnings("unchecked")
            Map<String, Object> orderData = payload.get("order") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : payload;

            SalesOrder order = new SalesOrder();
            order.setTenantId(event.getTenantId());
            order.setCustomerId(resolveCustomerId(event.getTenantId()));
            order.setNumber(sequenceService.nextNumber("SO", "SO-{YYYY}-{seq:5}"));
            order.setStatus("CONFIRMED");
            order.setChannel("SHOPIFY");
            order = salesOrderRepository.save(order);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lineItems = orderData.get("line_items") instanceof List<?> list
                    ? (List<Map<String, Object>>) list : List.of();

            boolean needsReview = false;
            for (Map<String, Object> item : lineItems) {
                String sku = item.getOrDefault("sku", "").toString();
                BigDecimal qty = new BigDecimal(item.getOrDefault("quantity", "1").toString());
                BigDecimal unitPrice = new BigDecimal(item.getOrDefault("price", "0").toString());
                var variantOpt = variantRepository.findByTenantIdAndSku(event.getTenantId(), sku);
                if (variantOpt.isEmpty()) {
                    needsReview = true;
                    continue;
                }
                ProductVariant variant = variantOpt.get();
                List<SoftKitExplosionService.ExplodedLine> exploded = softKitExplosionService.explode(
                        event.getTenantId(),
                        variant.getId(),
                        qty,
                        unitPrice,
                        false,
                        false);
                if (variant.isSoftKit() && exploded.isEmpty()) {
                    needsReview = true;
                    continue;
                }
                for (SoftKitExplosionService.ExplodedLine component : exploded) {
                    SalesOrderLine line = new SalesOrderLine();
                    line.setTenantId(event.getTenantId());
                    line.setSalesOrderId(order.getId());
                    line.setVariantId(component.variantId());
                    line.setQtyOrdered(component.quantity());
                    line.setUnitPrice(component.unitPrice());
                    salesOrderLineRepository.save(line);
                }
            }

            if (needsReview) {
                order.setStatus("NEEDS_REVIEW");
                salesOrderRepository.save(order);
            }

            IntegrationSyncLog log = new IntegrationSyncLog();
            log.setTenantId(event.getTenantId());
            log.setSystem("SHOPIFY");
            log.setEntityType("SALES_ORDER");
            log.setEntityId(order.getId());
            log.setStatus("SYNCED");
            syncLogRepository.save(log);

            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
    }

    private UUID resolveCustomerId(UUID tenantId) {
        return customerRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .findFirst()
                .map(Customer::getId)
                .orElseThrow(() -> new IllegalStateException("No customer configured for tenant"));
    }
}
