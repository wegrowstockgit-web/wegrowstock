package com.invsys;

import com.invsys.domain.EdiDocumentLog;
import com.invsys.domain.EdiTradingPartner;
import com.invsys.modules.fulfillment.domain.Shipment;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.domain.InvoiceLine;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceLineRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.fulfillment.repository.ShipmentRepository;
import com.invsys.repository.EdiDocumentLogRepository;
import com.invsys.repository.EdiTradingPartnerRepository;
import com.invsys.service.EdiTranslationEngine;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EdiOutbound856810Test extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired EdiTranslationEngine ediTranslationEngine;
    @Autowired EdiTradingPartnerRepository partnerRepository;
    @Autowired EdiDocumentLogRepository ediDocumentLogRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired ShipmentRepository shipmentRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired InvoiceLineRepository invoiceLineRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void generateOutbound856ContainsBsnAndManSegments() {
        UUID tenantId = testDataHelper.createTenant("EDI 856", "edi856-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = saveCustomer(tenantId, "Ship Customer");
        SalesOrder order = saveSalesOrder(tenantId, customer.getId(), "SO-856-1", "PO-856-99");

        Shipment shipment = new Shipment();
        shipment.setTenantId(tenantId);
        shipment.setSalesOrderId(order.getId());
        shipment.setCarrier("UPS");
        shipment.setTrackingNumber("1Z999AA10123456784");
        shipment.setStatus("SHIPPED");
        shipment = shipmentRepository.save(shipment);

        EdiTradingPartner partner = savePartner(tenantId, customer.getId());

        String payload = ediTranslationEngine.generateOutbound856Asn(shipment.getId());
        assertThat(payload).contains("ST*856*");
        assertThat(payload).contains("BSN*");
        assertThat(payload).contains("HL*");
        assertThat(payload).contains("TD5*");
        assertThat(payload).contains("REF*");
        assertThat(payload).contains("MAN*");

        EdiDocumentLog log = ediTranslationEngine.translateOutbound(
                partner.getId(), "856", shipment.getId(), Map.of("shipmentId", shipment.getId()));
        assertThat(log.getPayload()).contains("BSN*");
        assertThat(ediDocumentLogRepository.findAll()).isNotEmpty();
    }

    @Test
    void generateOutbound810ContainsBigAndTdsSegments() {
        UUID tenantId = testDataHelper.createTenant("EDI 810", "edi810-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = saveCustomer(tenantId, "Invoice Customer");
        SalesOrder order = saveSalesOrder(tenantId, customer.getId(), "SO-810-1", "PO-810-55");

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setCustomerId(customer.getId());
        invoice.setSalesOrderId(order.getId());
        invoice.setNumber("INV-810-1");
        invoice.setStatus("OPEN");
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(new BigDecimal("100.00"));
        invoice = invoiceRepository.save(invoice);

        InvoiceLine line = new InvoiceLine();
        line.setTenantId(tenantId);
        line.setInvoiceId(invoice.getId());
        line.setDescription("WIDGET-A");
        line.setQty(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setAmount(new BigDecimal("100.00"));
        invoiceLineRepository.save(line);

        EdiTradingPartner partner = savePartner(tenantId, customer.getId());

        String payload = ediTranslationEngine.generateOutbound810Invoice(invoice.getId());
        assertThat(payload).contains("ST*810*");
        assertThat(payload).contains("BIG*");
        assertThat(payload).contains("N1*");
        assertThat(payload).contains("IT1*");
        assertThat(payload).contains("TDS*");

        EdiDocumentLog log = ediTranslationEngine.translateOutbound(
                partner.getId(), "810", invoice.getId(), Map.of("invoiceId", invoice.getId()));
        assertThat(log.getPayload()).contains("BIG*");
        assertThat(log.getDocumentType()).isEqualTo("810");
    }

    private Customer saveCustomer(UUID tenantId, String name) {
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName(name);
        customer.setBillingAddress(new LinkedHashMap<>());
        customer.setShippingAddress(new LinkedHashMap<>());
        return customerRepository.save(customer);
    }

    private SalesOrder saveSalesOrder(UUID tenantId, UUID customerId, String number, String poNumber) {
        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customerId);
        order.setNumber(number);
        order.setCustomerPoNumber(poNumber);
        order.setStatus("SHIPPED");
        return salesOrderRepository.save(order);
    }

    private EdiTradingPartner savePartner(UUID tenantId, UUID customerId) {
        EdiTradingPartner partner = new EdiTradingPartner();
        partner.setTenantId(tenantId);
        partner.setCustomerId(customerId);
        partner.setAs2Id("PARTNER-" + UUID.randomUUID().toString().substring(0, 8));
        return partnerRepository.save(partner);
    }
}
