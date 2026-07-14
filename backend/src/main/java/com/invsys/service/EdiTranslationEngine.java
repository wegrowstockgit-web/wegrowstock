package com.invsys.service;

import com.invsys.domain.EdiDocumentLog;
import com.invsys.domain.EdiTradingPartner;
import com.invsys.repository.EdiDocumentLogRepository;
import com.invsys.repository.EdiTradingPartnerRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EdiTranslationEngine {

    private static final Pattern SEGMENT = Pattern.compile("([A-Z0-9]{2,3})\\*([^~]*)");

    private final EdiDocumentLogRepository documentLogRepository;
    private final EdiTradingPartnerRepository partnerRepository;

    public EdiTranslationEngine(EdiDocumentLogRepository documentLogRepository,
                                EdiTradingPartnerRepository partnerRepository) {
        this.documentLogRepository = documentLogRepository;
        this.partnerRepository = partnerRepository;
    }

    @Transactional
    public EdiDocumentLog translateOutbound(UUID partnerId, String documentType, UUID aggregateId,
                                            Map<String, Object> context) {
        UUID tenantId = TenantContext.requireTenantId();
        partnerRepository.findById(partnerId)
                .orElseThrow(() -> new IllegalArgumentException("Trading partner not found"));

        String payload = buildX12Payload(documentType, aggregateId, context);
        EdiDocumentLog log = new EdiDocumentLog();
        log.setTenantId(tenantId);
        log.setTradingPartnerId(partnerId);
        log.setDirection("OUTBOUND");
        log.setDocumentType(documentType);
        log.setPayload(payload);
        log.setStatus("SENT");
        return documentLogRepository.save(log);
    }

    @Transactional
    public InboundOrder parseInbound850(UUID partnerId, String payload) {
        UUID tenantId = TenantContext.requireTenantId();
        EdiTradingPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new IllegalArgumentException("Trading partner not found"));

        EdiDocumentLog log = new EdiDocumentLog();
        log.setTenantId(tenantId);
        log.setTradingPartnerId(partnerId);
        log.setDirection("INBOUND");
        log.setDocumentType("850");
        log.setPayload(payload);
        log.setStatus("PROCESSED");
        documentLogRepository.save(log);

        String poNumber = extractSegmentValue(payload, "BEG") != null
                ? extractField(extractSegmentValue(payload, "BEG"), 2)
                : "EDI-" + UUID.randomUUID().toString().substring(0, 8);

        List<InboundLine> lines = parsePo1Segments(payload);
        return new InboundOrder(partner.getId(), partner.getCustomerId(), poNumber, lines);
    }

    public List<EdiDocumentLog> listDocuments() {
        return documentLogRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId());
    }

    private String buildX12Payload(String documentType, UUID aggregateId, Map<String, Object> context) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String ref = aggregateId.toString();
        String ctx = context != null ? context.toString() : "";
        return "ISA*00*          *00*          *ZZ*INVSYS         *ZZ*PARTNER        *"
                + date + "*0001*U*00401*000000001*0*P*>~"
                + "ST*" + documentType + "*0001~"
                + "BEG*00*NE*" + ref + "**" + date + "~"
                + "REF*CTX*" + ctx.replace("*", "-") + "~"
                + "SE*4*0001~"
                + "GE*1*0001~"
                + "IEA*1*000000001~";
    }

    private List<InboundLine> parsePo1Segments(String payload) {
        return SEGMENT.matcher(payload).results()
                .filter(m -> "PO1".equals(m.group(1)))
                .map(m -> {
                    String[] fields = m.group(2).split("\\*", -1);
                    String sku = fields.length > 6 ? fields[6]
                            : (fields.length > 0 ? fields[0] : "UNKNOWN");
                    String qty = fields.length > 1 && fields[0].matches("\\d+(\\.\\d+)?") ? fields[0] : "1";
                    return new InboundLine(sku, new java.math.BigDecimal(qty));
                })
                .toList();
    }

    private String extractSegmentValue(String payload, String segmentId) {
        Matcher matcher = SEGMENT.matcher(payload);
        while (matcher.find()) {
            if (segmentId.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }

    private String extractField(String segmentBody, int index) {
        String[] fields = segmentBody.split("\\*");
        return index < fields.length ? fields[index] : "";
    }

    public record InboundLine(String sku, java.math.BigDecimal quantity) {
    }

    public record InboundOrder(UUID partnerId, UUID customerId, String poNumber, List<InboundLine> lines) {
    }
}
