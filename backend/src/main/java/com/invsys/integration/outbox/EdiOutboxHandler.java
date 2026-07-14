package com.invsys.integration.outbox;

import com.invsys.domain.EdiTradingPartner;
import com.invsys.repository.EdiTradingPartnerRepository;
import com.invsys.service.EdiTranslationEngine;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EdiOutboxHandler implements com.invsys.integration.OutboxEventHandler {

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
        return List.of("SALES_ORDER_CONFIRMED", "SHIPMENT_CREATED", "INVOICE_OPEN");
    }

    @Override
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        TenantContext.setTenantId(tenantId);
        try {
            String ediType = switch (eventType) {
                case "SALES_ORDER_CONFIRMED" -> "855";
                case "SHIPMENT_CREATED" -> "856";
                case "INVOICE_OPEN" -> "810";
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
            ediTranslationEngine.translateOutbound(partner.getId(), ediType, aggregateId, payload);
        } finally {
            TenantContext.clear();
        }
    }
}
