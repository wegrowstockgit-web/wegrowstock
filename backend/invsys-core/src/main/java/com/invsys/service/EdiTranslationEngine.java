package com.invsys.service;

import com.invsys.domain.EdiDocumentLog;
import com.invsys.domain.EdiTradingPartner;
import com.invsys.modules.fulfillment.domain.Shipment;
import com.invsys.modules.fulfillment.domain.ShipmentLine;
import com.invsys.modules.fulfillment.repository.ShipmentLineRepository;
import com.invsys.modules.fulfillment.repository.ShipmentRepository;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.domain.InvoiceLine;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceLineRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.repository.EdiDocumentLogRepository;
import com.invsys.repository.EdiTradingPartnerRepository;
import com.invsys.core.tenancy.TenantContext;
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
    private final ShipmentRepository shipmentRepository;
    private final ShipmentLineRepository shipmentLineRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;

    public EdiTranslationEngine(EdiDocumentLogRepository documentLogRepository,
                                EdiTradingPartnerRepository partnerRepository,
                                ShipmentRepository shipmentRepository,
                                ShipmentLineRepository shipmentLineRepository,
                                InvoiceRepository invoiceRepository,
                                InvoiceLineRepository invoiceLineRepository,
                                SalesOrderRepository salesOrderRepository,
                                CustomerRepository customerRepository) {
        this.documentLogRepository = documentLogRepository;
        this.partnerRepository = partnerRepository;
        this.shipmentRepository = shipmentRepository;
        this.shipmentLineRepository = shipmentLineRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
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

    public String generateOutbound856Asn(UUID shipmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .filter(s -> tenantId.equals(s.getTenantId()))
                .orElse(null);

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String time = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
        String control = shortRef(shipmentId);
        String shipmentNumber = shipment != null ? shortRef(shipment.getId()) : control;
        String carrier = shipment != null && shipment.getCarrier() != null ? shipment.getCarrier() : "GENERIC";
        String tracking = shipment != null && shipment.getTrackingNumber() != null
                ? shipment.getTrackingNumber() : "TRACK-" + control;
        String sscc = "000" + control.replace("-", "") + "000000001";

        SalesOrder order = null;
        Customer customer = null;
        if (shipment != null) {
            order = salesOrderRepository.findById(shipment.getSalesOrderId()).orElse(null);
            if (order != null) {
                customer = customerRepository.findById(order.getCustomerId()).orElse(null);
            }
        }
        String poRef = order != null && order.getCustomerPoNumber() != null
                ? order.getCustomerPoNumber()
                : (order != null ? order.getNumber() : "PO-" + control);
        String shipTo = customer != null ? customer.getName() : "CONSIGNEE";

        List<ShipmentLine> lines = shipmentLineRepository.findByShipmentId(shipmentId);
        int lineCount = Math.max(lines.size(), 1);

        StringBuilder body = new StringBuilder();
        body.append("BSN*00*").append(shipmentNumber).append('*').append(date).append('*').append(time).append("~");
        body.append("HL*1**S~");
        body.append("TD5*B*2*").append(sanitize(carrier)).append("~");
        body.append("REF*BM*").append(sanitize(tracking)).append("~");
        body.append("REF*CN*").append(sanitize(poRef)).append("~");
        body.append("MAN*GM*").append(sanitize(sscc)).append("~");

        int hl = 2;
        if (lines.isEmpty()) {
            body.append("HL*").append(hl).append("*1*O~");
            body.append("PRF*").append(sanitize(poRef)).append("~");
            body.append("HL*").append(hl + 1).append('*').append(hl).append("*I~");
            body.append("LIN**SK*ITEM-001~");
            body.append("SN1**1*EA~");
        } else {
            for (ShipmentLine line : lines) {
                body.append("HL*").append(hl).append("*1*O~");
                body.append("PRF*").append(sanitize(poRef)).append("~");
                body.append("HL*").append(hl + 1).append('*').append(hl).append("*I~");
                body.append("LIN**VN*").append(shortRef(line.getSalesOrderLineId())).append("~");
                body.append("SN1**").append(line.getQuantity() != null ? line.getQuantity().toPlainString() : "1")
                        .append("*EA~");
                hl += 2;
            }
        }

        int segmentCount = countSegments(body.toString()) + 2;
        return wrapX12("856", control, body.toString(), segmentCount, shipTo);
    }

    public String generateOutbound810Invoice(UUID invoiceId) {
        UUID tenantId = TenantContext.requireTenantId();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(i -> tenantId.equals(i.getTenantId()))
                .orElse(null);

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String control = shortRef(invoiceId);
        String invoiceNumber = invoice != null ? invoice.getNumber() : "INV-" + control;
        String poRef = "PO-" + control;
        Customer customer = null;
        if (invoice != null) {
            customer = customerRepository.findById(invoice.getCustomerId()).orElse(null);
            if (invoice.getSalesOrderId() != null) {
                SalesOrder order = salesOrderRepository.findById(invoice.getSalesOrderId()).orElse(null);
                if (order != null && order.getCustomerPoNumber() != null) {
                    poRef = order.getCustomerPoNumber();
                } else if (order != null) {
                    poRef = order.getNumber();
                }
            }
        }
        String customerName = customer != null ? customer.getName() : "BILL-TO";
        String total = invoice != null && invoice.getTotal() != null
                ? invoice.getTotal().toPlainString() : "0.00";

        StringBuilder body = new StringBuilder();
        body.append("BIG*").append(date).append('*').append(invoiceNumber).append('*').append(date)
                .append('*').append(sanitize(poRef)).append("~");
        body.append("N1*BT*").append(sanitize(customerName)).append("~");
        body.append("N1*ST*").append(sanitize(customerName)).append("~");

        List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceId(invoiceId);
        int lineNum = 1;
        if (lines.isEmpty()) {
            body.append("IT1*").append(lineNum).append("*1*EA*0.00**VN*ITEM-001~");
        } else {
            for (InvoiceLine line : lines) {
                String qty = line.getQty() != null ? line.getQty().toPlainString() : "1";
                String price = line.getUnitPrice() != null ? line.getUnitPrice().toPlainString() : "0.00";
                String sku = line.getDescription() != null ? line.getDescription() : "LINE-" + lineNum;
                body.append("IT1*").append(lineNum++).append('*').append(qty).append("*EA*")
                        .append(price).append("**VN*").append(sanitize(sku)).append("~");
            }
        }
        body.append("TDS*").append(total.replace(".", "")).append("~");

        int segmentCount = countSegments(body.toString()) + 2;
        return wrapX12("810", control, body.toString(), segmentCount, customerName);
    }

    private String buildX12Payload(String documentType, UUID aggregateId, Map<String, Object> context) {
        if (aggregateId != null) {
            if ("856".equals(documentType)) {
                return generateOutbound856Asn(aggregateId);
            }
            if ("810".equals(documentType)) {
                return generateOutbound810Invoice(aggregateId);
            }
        }
        return buildGenericX12Payload(documentType, aggregateId, context);
    }

    private String buildGenericX12Payload(String documentType, UUID aggregateId, Map<String, Object> context) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String ref = aggregateId != null ? aggregateId.toString() : "00000000";
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

    private String wrapX12(String documentType, String control, String body, int segmentCount, String partyName) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String stControl = control.replace("-", "").substring(0, Math.min(4, control.replace("-", "").length()));
        if (stControl.isEmpty()) {
            stControl = "0001";
        }
        StringBuilder payload = new StringBuilder();
        payload.append("ISA*00*          *00*          *ZZ*INVSYS         *ZZ*")
                .append(pad(partyName, 15)).append('*')
                .append(date).append("*0001*U*00401*000000001*0*P*>~");
        payload.append("GS*SH*INVSYS*PARTNER*").append(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .append("*0001*1*X*004010~");
        payload.append("ST*").append(documentType).append('*').append(stControl).append("~");
        payload.append(body);
        payload.append("SE*").append(segmentCount).append('*').append(stControl).append("~");
        payload.append("GE*1*1~");
        payload.append("IEA*1*000000001~");
        return payload.toString();
    }

    private static int countSegments(String body) {
        if (body == null || body.isEmpty()) {
            return 0;
        }
        return (int) body.chars().filter(ch -> ch == '~').count();
    }

    private static String shortRef(UUID id) {
        return id == null ? "00000000" : id.toString().substring(0, 8).toUpperCase();
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("*", "-").replace("~", "-").trim();
    }

    private static String pad(String value, int len) {
        String v = sanitize(value);
        if (v.length() >= len) {
            return v.substring(0, len);
        }
        return v + " ".repeat(len - v.length());
    }

    private List<InboundLine> parsePo1Segments(String payload) {
        return SEGMENT.matcher(payload).results()
                .filter(m -> "PO1".equals(m.group(1)))
                .map(m -> {
                    String[] fields = m.group(2).split("\\*", -1);
                    String sku = extractPo1Sku(fields);
                    String qty = fields.length > 0 && fields[0].matches("\\d+(\\.\\d+)?") ? fields[0] : "1";
                    return new InboundLine(sku, new java.math.BigDecimal(qty));
                })
                .toList();
    }

    private static String extractPo1Sku(String[] fields) {
        for (int i = 0; i < fields.length - 1; i++) {
            String qual = fields[i] != null ? fields[i].trim().toUpperCase() : "";
            if (qual.matches("VP|VN|SK|BP|UP|UI")) {
                String id = fields[i + 1] != null ? fields[i + 1].trim() : "";
                if (!id.isBlank()) {
                    return id;
                }
            }
        }
        if (fields.length > 6 && fields[6] != null && !fields[6].isBlank()) {
            return fields[6].trim();
        }
        return fields.length > 0 && fields[0] != null && !fields[0].isBlank() ? fields[0].trim() : "UNKNOWN";
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
