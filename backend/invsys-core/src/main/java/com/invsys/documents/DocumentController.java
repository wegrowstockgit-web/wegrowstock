package com.invsys.documents;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.sales.repository.InvoiceRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final InvoiceDocumentBuilder invoiceDocumentBuilder;
    private final PackingSlipDocumentBuilder packingSlipDocumentBuilder;
    private final DocumentDispatchService documentDispatchService;
    private final DocumentArchivalService documentArchivalService;
    private final InvoiceRepository invoiceRepository;

    public DocumentController(
            InvoiceDocumentBuilder invoiceDocumentBuilder,
            PackingSlipDocumentBuilder packingSlipDocumentBuilder,
            DocumentDispatchService documentDispatchService,
            DocumentArchivalService documentArchivalService,
            InvoiceRepository invoiceRepository
    ) {
        this.invoiceDocumentBuilder = invoiceDocumentBuilder;
        this.packingSlipDocumentBuilder = packingSlipDocumentBuilder;
        this.documentDispatchService = documentDispatchService;
        this.documentArchivalService = documentArchivalService;
        this.invoiceRepository = invoiceRepository;
    }

    @GetMapping("/invoice/{id}/pdf")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        Invoice invoice = invoiceRepository.findById(id)
                .filter(i -> tenantId.equals(i.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));

        byte[] pdf = invoiceDocumentBuilder.buildPdf(id);
        // Best-effort archival so OPEN invoices retain an immutable document_url.
        try {
            documentArchivalService.archiveInvoicePdfBytes(id, pdf);
        } catch (RuntimeException ignored) {
            // Download still succeeds even if object storage is temporarily unavailable.
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + sanitizeFilename(invoice.getNumber()) + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/invoice/{id}/email")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public Map<String, Object> emailInvoice(@PathVariable("id") UUID id) {
        return documentDispatchService.emailInvoice(id);
    }

    @GetMapping("/packing-slip/{shipmentId}/pdf")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<byte[]> packingSlipPdf(@PathVariable UUID shipmentId) {
        byte[] pdf = packingSlipDocumentBuilder.buildPdf(shipmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"packing-slip-" + shipmentId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private static String sanitizeFilename(String number) {
        if (number == null || number.isBlank()) {
            return "invoice";
        }
        return number.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
