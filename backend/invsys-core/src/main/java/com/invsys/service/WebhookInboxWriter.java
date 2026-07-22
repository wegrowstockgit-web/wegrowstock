package com.invsys.service;

import com.invsys.domain.WebhookEvent;
import com.invsys.repository.WebhookEventRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class WebhookInboxWriter {

    private final WebhookEventRepository webhookEventRepository;
    private final ObjectProvider<WebhookInboxWriter> self;

    public WebhookInboxWriter(WebhookEventRepository webhookEventRepository,
                              ObjectProvider<WebhookInboxWriter> self) {
        this.webhookEventRepository = webhookEventRepository;
        this.self = self;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WebhookEvent insertIfAbsent(String source,
                                       String externalEventId,
                                       boolean signatureValid,
                                       Map<String, Object> payload,
                                       UUID tenantId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return self.getObject().attemptInsert(source, externalEventId, signatureValid, payload, tenantId);
            } catch (DuplicateWebhookEventException duplicate) {
                try {
                    return self.getObject().loadExisting(source, externalEventId, tenantId);
                } catch (DataIntegrityViolationException notVisible) {
                    sleepBriefly(attempt);
                }
            }
        }
        return self.getObject().loadExisting(source, externalEventId, tenantId);
    }

    private void sleepBriefly(int attempt) {
        try {
            Thread.sleep(20L * (attempt + 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookEvent attemptInsert(String source,
                                      String externalEventId,
                                      boolean signatureValid,
                                      Map<String, Object> payload,
                                      UUID tenantId) {
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        }
        try {
            var existing = webhookEventRepository.findBySourceAndExternalEventId(source, externalEventId);
            if (existing.isPresent()) {
                return existing.get();
            }

            WebhookEvent event = new WebhookEvent();
            event.setSource(source);
            event.setExternalEventId(externalEventId);
            event.setSignatureValid(signatureValid);
            event.setPayload(payload);
            if (tenantId != null) {
                event.setTenantId(tenantId);
            }
            try {
                return webhookEventRepository.saveAndFlush(event);
            } catch (RuntimeException ex) {
                if (isDuplicateKey(ex)) {
                    throw new DuplicateWebhookEventException(ex);
                }
                throw ex;
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public WebhookEvent loadExisting(String source, String externalEventId, UUID tenantId) {
        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        }
        try {
            return webhookEventRepository.findBySourceAndExternalEventId(source, externalEventId)
                    .orElseThrow(() -> new DataIntegrityViolationException(
                            "Duplicate webhook event not visible after conflict"));
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isDuplicateKey(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("uq_webhook_tenant_source_event")
                    || message.contains("duplicate key"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class DuplicateWebhookEventException extends RuntimeException {
        DuplicateWebhookEventException(Throwable cause) {
            super(cause);
        }
    }
}
