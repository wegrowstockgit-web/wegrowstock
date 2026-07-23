package com.invsys.integration.outbox;

import com.invsys.core.integration.OutboxEventHandler;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.service.ThermalPrintingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ThermalPrintOutboxHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ThermalPrintOutboxHandler.class);

    private final ThermalPrintingService thermalPrintingService;

    public ThermalPrintOutboxHandler(ThermalPrintingService thermalPrintingService) {
        this.thermalPrintingService = thermalPrintingService;
    }

    @Override
    public String eventType() {
        return "SHIPMENT_CREATED";
    }

    @Override
    public List<String> eventTypes() {
        return List.of("SHIPMENT_CREATED", "LPN_MINTED");
    }

    @Override
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        TenantContext.setTenantId(tenantId);
        try {
            if (thermalPrintingService.resolveDefaultPrinter().isEmpty()) {
                log.debug("Skipping thermal print — no default printer tenant={}", tenantId);
                return;
            }
            String zpl = buildZpl(eventType, aggregateId, payload);
            thermalPrintingService.printZplToDefault(zpl);
        } catch (Exception ex) {
            log.warn("Thermal print failed tenant={} event={} aggregateId={}: {}",
                    tenantId, eventType, aggregateId, ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private String buildZpl(String eventType, UUID aggregateId, Map<String, Object> payload) {
        return switch (eventType) {
            case "LPN_MINTED" -> {
                String barcode = stringValue(payload, "lpnBarcode", aggregateId.toString());
                yield thermalPrintingService.buildSimpleLabelZpl("LICENSE PLATE", barcode, "Pallet / bulk move");
            }
            case "SHIPMENT_CREATED" -> {
                String tracking = stringValue(payload, "trackingNumber", aggregateId.toString());
                String carrier = stringValue(payload, "carrier", "SHIPMENT");
                yield thermalPrintingService.buildSimpleLabelZpl("SHIPMENT", tracking, carrier);
            }
            default -> thermalPrintingService.buildSimpleLabelZpl("LABEL", aggregateId.toString(), eventType);
        };
    }

    private static String stringValue(Map<String, Object> payload, String key, String fallback) {
        if (payload == null || !payload.containsKey(key) || payload.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(payload.get(key)).trim();
        return value.isEmpty() ? fallback : value;
    }
}
