package com.invsys.api;

import com.invsys.domain.SupplierInvoiceIngestion;
import com.invsys.modules.purchasing.service.ApInvoiceWorkspaceService;
import com.invsys.modules.purchasing.service.ApOcrIngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final ApInvoiceWorkspaceService workspaceService;

    public ApIngestionController(ApOcrIngestionService apOcrIngestionService,
                                 ApInvoiceWorkspaceService workspaceService) {
        this.apOcrIngestionService = apOcrIngestionService;
        this.workspaceService = workspaceService;
    }

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApInvoiceWorkspaceService.ApWorkspaceResponse ingest(@RequestPart("file") MultipartFile file) {
        return workspaceService.ingest(file);
    }

    @PostMapping("/workspace/bind")
    public ApInvoiceWorkspaceService.ApWorkspaceResponse bind(@Valid @RequestBody BindWorkspaceRequest request) {
        return workspaceService.bindPurchaseOrder(request.purchaseOrderId(), request.extractedData());
    }

    @PostMapping("/workspace/preview")
    public ApInvoiceWorkspaceService.ApWorkspaceResponse preview(@Valid @RequestBody BindWorkspaceRequest request) {
        return workspaceService.preview(request.purchaseOrderId(), request.extractedData());
    }

    @PostMapping("/ingestions/{id}/approve")
    public ApInvoiceWorkspaceService.ApWorkspaceResponse approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveRequest body) {
        return workspaceService.approve(id, body == null ? null : body.lines());
    }

    @PostMapping("/ingestions/{id}/dispute")
    public ApInvoiceWorkspaceService.DisputeResponse dispute(@PathVariable UUID id) {
        return workspaceService.dispute(id);
    }

    @PostMapping("/ingestions/{id}/request-recount")
    public ApInvoiceWorkspaceService.RecountResponse requestRecount(@PathVariable UUID id) {
        return workspaceService.requestRecount(id);
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
            @Size(max = 1024) String documentUrl,
            Map<String, Object> extractedData
    ) {
    }

    public record BindWorkspaceRequest(
            @NotNull UUID purchaseOrderId,
            Map<String, Object> extractedData
    ) {
    }

    public record ApproveRequest(List<Map<String, Object>> lines) {
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
