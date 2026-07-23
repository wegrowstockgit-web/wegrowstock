package com.invsys.documents;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.media.MediaStorageProperties;
import com.invsys.media.ObjectStorage;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Uploads generated PDFs to S3-compatible storage and persists the immutable URL on the invoice.
 * Object key: {@code {tenantId}/invoices/{invoiceId}.pdf}
 * Stored URL: {@code s3://{bucket}/{key}} (logical; bucket defaults to media bucket).
 */
@Service
public class DocumentArchivalService {

    private static final Logger log = LoggerFactory.getLogger(DocumentArchivalService.class);

    private final ObjectStorage objectStorage;
    private final MediaStorageProperties mediaProperties;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceDocumentBuilder invoiceDocumentBuilder;

    public DocumentArchivalService(
            ObjectStorage objectStorage,
            MediaStorageProperties mediaProperties,
            InvoiceRepository invoiceRepository,
            InvoiceDocumentBuilder invoiceDocumentBuilder
    ) {
        this.objectStorage = objectStorage;
        this.mediaProperties = mediaProperties;
        this.invoiceRepository = invoiceRepository;
        this.invoiceDocumentBuilder = invoiceDocumentBuilder;
    }

    /**
     * Generate (if needed) and archive the invoice PDF when status is OPEN (or already open).
     * Idempotent when {@code document_url} is already set.
     */
    @Transactional
    public String archiveInvoicePdf(UUID invoiceId) {
        UUID tenantId = TenantContext.requireTenantId();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(i -> tenantId.equals(i.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));

        if (invoice.getDocumentUrl() != null && !invoice.getDocumentUrl().isBlank()) {
            return invoice.getDocumentUrl();
        }

        byte[] pdf = invoiceDocumentBuilder.buildPdf(invoiceId);
        return storeInvoicePdf(invoice, pdf);
    }

    @Transactional
    public String archiveInvoicePdfBytes(UUID invoiceId, byte[] pdf) {
        UUID tenantId = TenantContext.requireTenantId();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(i -> tenantId.equals(i.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));
        if (invoice.getDocumentUrl() != null && !invoice.getDocumentUrl().isBlank()) {
            return invoice.getDocumentUrl();
        }
        return storeInvoicePdf(invoice, pdf);
    }

    private String storeInvoicePdf(Invoice invoice, byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EMPTY_PDF", "PDF bytes missing");
        }
        String key = invoice.getTenantId() + "/invoices/" + invoice.getId() + ".pdf";
        objectStorage.put(key, pdf, "application/pdf");
        String bucket = mediaProperties.getBucket() == null || mediaProperties.getBucket().isBlank()
                ? "invsys-documents"
                : mediaProperties.getBucket();
        // Spec path shape: s3://invsys-documents/{tenant}/invoices/{id}.pdf — use configured bucket.
        String url = "s3://" + bucket + "/" + key;
        invoice.setDocumentUrl(url);
        invoiceRepository.save(invoice);
        log.info("Archived invoice PDF invoiceId={} url={}", invoice.getId(), url);
        return url;
    }
}
