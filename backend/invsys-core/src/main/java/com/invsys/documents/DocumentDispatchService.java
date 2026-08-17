package com.invsys.documents;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.Tenant;
import com.invsys.mail.TenantMailSender;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.repository.TenantRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Emails generated PDF attachments to customers via {@link JavaMailSender}.
 */
@Service
public class DocumentDispatchService {

    private static final Logger log = LoggerFactory.getLogger(DocumentDispatchService.class);

    private final TenantMailSender tenantMailSender;
    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceDocumentBuilder invoiceDocumentBuilder;
    private final DocumentArchivalService archivalService;

    public DocumentDispatchService(
            TenantMailSender tenantMailSender,
            InvoiceRepository invoiceRepository,
            CustomerRepository customerRepository,
            TenantRepository tenantRepository,
            InvoiceDocumentBuilder invoiceDocumentBuilder,
            DocumentArchivalService archivalService
    ) {
        this.tenantMailSender = tenantMailSender;
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.tenantRepository = tenantRepository;
        this.invoiceDocumentBuilder = invoiceDocumentBuilder;
        this.archivalService = archivalService;
    }

    /**
     * Generate invoice PDF, archive to S3, and email the customer.
     *
     * @return result map with {@code sent}, {@code documentUrl}, {@code to}
     */
    public Map<String, Object> emailInvoice(UUID invoiceId) {
        UUID tenantId = TenantContext.requireTenantId();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(i -> tenantId.equals(i.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));
        Customer customer = customerRepository.findById(invoice.getCustomerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Customer not found"));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tenant not found"));

        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_CUSTOMER_EMAIL",
                    "Customer has no email address for invoice delivery");
        }

        byte[] pdf = invoiceDocumentBuilder.buildPdf(invoiceId);
        String documentUrl = archivalService.archiveInvoicePdfBytes(invoiceId, pdf);

        String subject = "Your Invoice " + invoice.getNumber() + " from " + tenant.getName();
        String body = """
                Hello %s,

                Please find attached invoice %s from %s.
                Total: %s %s

                Thank you for your business.
                """.formatted(
                customer.getName(),
                invoice.getNumber(),
                tenant.getName(),
                invoice.getTotal(),
                invoice.getCurrency());

        boolean sent = sendPdf(customer.getEmail().trim(), subject, body, invoice.getNumber() + ".pdf", pdf);
        return Map.of(
                "sent", sent,
                "to", customer.getEmail().trim(),
                "documentUrl", documentUrl,
                "invoiceNumber", invoice.getNumber());
    }

    public boolean sendPdf(String to, String subject, String textBody, String filename, byte[] pdf) {
        if (to == null || to.isBlank() || pdf == null || pdf.length == 0) {
            return false;
        }
        JavaMailSender mailSender = tenantMailSender.current();
        if (mailSender == null) {
            log.warn("SMTP not configured — invoice email not sent to={} subject={} bytes={}",
                    to, subject, pdf.length);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(tenantMailSender.fromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, false);
            helper.addAttachment(
                    filename == null || filename.isBlank() ? "document.pdf" : filename,
                    new ByteArrayResource(pdf),
                    "application/pdf");
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Invoice email dispatch failed to={}: {}", to, ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_DISPATCH_FAILED",
                    "Could not send invoice email");
        }
    }
}
