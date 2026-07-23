package com.invsys.integration.outbox;

import com.invsys.domain.EdiTradingPartner;
import com.invsys.repository.EdiTradingPartnerRepository;
import com.invsys.service.EdiTranslationEngine;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EdiOutboxHandler implements com.invsys.core.integration.OutboxEventHandler {

    private final EdiTradingPartnerRepository partnerRepository;
    private final EdiTranslationEngine ediTranslationEngine;

    public EdiOutboxHandler(EdiTradingPartnerRepository partnerRepository,
                            EdiTranslationEngine ediTranslationEngine) {
        this.partnerRepository = partnerRepository;
        this.ediTranslationEngine = ediTranslationEngine;
    }

    @Override
    public String eventType() {
        return "SALES_ORDER_CONFIRMED";
    }

    @Override
    public List<String> eventTypes() {
        return List.of(
                "SALES_ORDER_CONFIRMED",
                "SHIPMENT_CREATED",
                "SHIPMENT_SHIPPED",
                "INVOICE_OPEN",
                "INVOICE_PAID");
    }

    @Override
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        TenantContext.setTenantId(tenantId);
        try {
            String ediType = switch (eventType) {
                case "SALES_ORDER_CONFIRMED" -> "855";
                case "SHIPMENT_CREATED", "SHIPMENT_SHIPPED" -> "856";
                case "INVOICE_OPEN", "INVOICE_PAID" -> "810";
                default -> null;
            };
            if (ediType == null) {
                return;
            }
            List<EdiTradingPartner> partners = partnerRepository.findByTenantId(tenantId);
            if (partners.isEmpty()) {
                return;
            }
            EdiTradingPartner partner = partners.get(0);
            UUID docAggregateId = resolveAggregateId(eventType, aggregateId, payload);
            ediTranslationEngine.translateOutbound(partner.getId(), ediType, docAggregateId, payload);
        } finally {
            TenantContext.clear();
        }
    }

    private static UUID resolveAggregateId(String eventType, UUID aggregateId, Map<String, Object> payload) {
        if (payload == null) {
            return aggregateId;
        }
        return switch (eventType) {
            case "INVOICE_OPEN", "INVOICE_PAID" -> uuidFromPayload(payload, "invoiceId", aggregateId);
            case "SHIPMENT_CREATED", "SHIPMENT_SHIPPED" -> uuidFromPayload(payload, "shipmentId", aggregateId);
            default -> aggregateId;
        };
    }

    private static UUID uuidFromPayload(Map<String, Object> payload, String key, UUID fallback) {
        Object value = payload.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
