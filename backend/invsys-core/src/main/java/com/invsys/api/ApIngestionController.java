package com.invsys.api;

import com.invsys.domain.SupplierInvoiceIngestion;
import com.invsys.modules.purchasing.service.ApOcrIngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ap")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class ApIngestionController {

    private final ApOcrIngestionService apOcrIngestionService;

    public ApIngestionController(ApOcrIngestionService apOcrIngestionService) {
        this.apOcrIngestionService = apOcrIngestionService;
    }

    @GetMapping("/ingestions")
    public List<IngestionResponse> listIngestions() {
        return apOcrIngestionService.listForTenant().stream().map(this::toResponse).toList();
    }

    @GetMapping("/ingestions/purchase-order/{purchaseOrderId}")
    public List<IngestionResponse> listForPurchaseOrder(@PathVariable UUID purchaseOrderId) {
        return apOcrIngestionService.listForPurchaseOrder(purchaseOrderId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/ingestions")
    public IngestionResponse submit(@Valid @RequestBody SubmitIngestionRequest request) {
        return toResponse(apOcrIngestionService.submitDocument(
                request.purchaseOrderId(), request.extractedData(), request.documentUrl()));
    }

    private IngestionResponse toResponse(SupplierInvoiceIngestion ingestion) {
        return new IngestionResponse(
                ingestion.getId(),
                ingestion.getPurchaseOrderId(),
                ingestion.getStatus(),
                ingestion.getDocumentUrl(),
                ingestion.getMatchConfidence(),
                ingestion.getExtractedData(),
                ingestion.getCreatedAt()
        );
    }

    public record SubmitIngestionRequest(
            @NotNull UUID purchaseOrderId,
            String documentUrl,
            Map<String, Object> extractedData
    ) {
    }

    public record IngestionResponse(
            UUID id,
            UUID purchaseOrderId,
            String status,
            String documentUrl,
            BigDecimal matchConfidence,
            Map<String, Object> extractedData,
            Instant createdAt
    ) {
    }
}
