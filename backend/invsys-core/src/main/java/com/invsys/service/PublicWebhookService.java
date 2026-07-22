package com.invsys.service;

import com.invsys.domain.WebhookEvent;
import com.invsys.repository.WebhookEventRepository;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PublicWebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final BootstrapJdbc bootstrapJdbc;

    public PublicWebhookService(WebhookEventRepository webhookEventRepository, BootstrapJdbc bootstrapJdbc) {
        this.webhookEventRepository = webhookEventRepository;
        this.bootstrapJdbc = bootstrapJdbc;
    }

    @Transactional
    public WebhookEvent ingestChannel(String platform, String shopDomain, String externalEventId,
                                      Map<String, Object> payload) {
        String prefixedId = shopDomain + ":" + externalEventId;
        String source = platform.toUpperCase();
        Optional<WebhookEvent> existing = webhookEventRepository.findBySourceAndExternalEventId(source, prefixedId);
        if (existing.isPresent()) {
            return existing.get();
        }

        WebhookEvent event = new WebhookEvent();
        event.setSource(source);
        event.setExternalEventId(prefixedId);
        event.setSignatureValid(true);
        event.setPayload(payload);

        bootstrapJdbc.findTenantIdByChannelShop(platform.toUpperCase(), shopDomain)
                .ifPresent(event::setTenantId);

        return webhookEventRepository.save(event);
    }

    @Transactional
    public WebhookEvent ingestEasyPost(String externalEventId, Map<String, Object> payload) {
        Optional<WebhookEvent> existing = webhookEventRepository.findBySourceAndExternalEventId("EASYPOST", externalEventId);
        if (existing.isPresent()) {
            return existing.get();
        }
        WebhookEvent event = new WebhookEvent();
        event.setSource("EASYPOST");
        event.setExternalEventId(externalEventId);
        event.setSignatureValid(true);
        event.setPayload(payload);
        return webhookEventRepository.save(event);
    }

    public Map<String, Object> toPayloadMap(String rawBody) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("raw", rawBody);
        return payload;
    }
}
