package com.invsys.api;

import com.invsys.domain.ApInvoiceIngestion;
import com.invsys.service.ApDocumentIngestionService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ap-ingestions")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class ApInvoiceIngestionController {

    private final ApDocumentIngestionService ingestionService;

    public ApInvoiceIngestionController(ApDocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApIngestionResponse upload(@RequestPart("file") MultipartFile file) {
        return toResponse(ingestionService.uploadAndEnqueue(file));
    }

    @GetMapping
    public List<ApIngestionResponse> list() {
        return ingestionService.listForTenant().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ApIngestionResponse get(@PathVariable UUID id) {
        return toResponse(ingestionService.get(id));
    }

    private ApIngestionResponse toResponse(ApInvoiceIngestion ingestion) {
        return new ApIngestionResponse(
                ingestion.getId(),
                ingestion.getFileStorageKey(),
                ingestion.getIngestionStatus(),
                ingestion.getParsedMetadata(),
                ingestion.getMatchedPurchaseOrderId(),
                ingestion.getCreatedAt());
    }

    public record ApIngestionResponse(
            UUID id,
            String fileStorageKey,
            String ingestionStatus,
            Map<String, Object> parsedMetadata,
            UUID matchedPurchaseOrderId,
            Instant createdAt
    ) {
    }
}
