package com.invsys.service;

import com.invsys.common.MdcSupport;
import com.invsys.domain.WebhookEvent;
import com.invsys.repository.WebhookEventRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookEventProcessor {

    private final WebhookEventRepository webhookEventRepository;
    private final InvoicingService invoicingService;

    public WebhookEventProcessor(WebhookEventRepository webhookEventRepository,
                                 InvoicingService invoicingService) {
        this.webhookEventRepository = webhookEventRepository;
        this.invoicingService = invoicingService;
    }

    @Async("virtualThreadExecutor")
    public void processAsync(UUID eventId) {
        webhookEventRepository.findById(eventId).ifPresent(this::process);
    }

    @Transactional
    public void process(WebhookEvent event) {
        if (event.getProcessedAt() != null) {
            return;
        }
        MdcSupport.run(
                event.getTenantId(),
                MdcSupport.backgroundRequestId("webhook", event.getId()),
                null,
                () -> {
                    if (event.getTenantId() != null) {
                        TenantContext.setTenantId(event.getTenantId());
                    }
                    try {
                        String type = (String) event.getPayload().getOrDefault("type", "");
                        if ("payment_intent.succeeded".equals(type)) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = (Map<String, Object>) event.getPayload().get("data");
                            if (data != null) {
                                String externalId = (String) data.get("id");
                                if (externalId != null) {
                                    invoicingService.settlePayment(externalId);
                                }
                            }
                        }
                        event.setProcessedAt(Instant.now());
                        webhookEventRepository.save(event);
                    } catch (Exception e) {
                        event.setError(e.getMessage());
                        webhookEventRepository.save(event);
                    } finally {
                        TenantContext.clear();
                    }
                    return null;
                });
    }
}
