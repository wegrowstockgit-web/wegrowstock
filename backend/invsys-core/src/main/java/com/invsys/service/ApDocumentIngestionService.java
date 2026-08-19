package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.purchasing.domain.ApInvoiceIngestion;
import com.invsys.media.ObjectStorage;
import com.invsys.modules.purchasing.repository.ApInvoiceIngestionRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class ApDocumentIngestionService {

    private static final long MAX_BYTES = 15 * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".png", ".jpg", ".jpeg", ".csv", ".txt");

    private final ApInvoiceIngestionRepository ingestionRepository;
    private final ObjectStorage objectStorage;
    private final ApDocumentParseService parseService;
    private final Executor virtualThreadExecutor;

    public ApDocumentIngestionService(ApInvoiceIngestionRepository ingestionRepository,
                                      ObjectStorage objectStorage,
                                      ApDocumentParseService parseService,
                                      @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
        this.ingestionRepository = ingestionRepository;
        this.objectStorage = objectStorage;
        this.parseService = parseService;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Transactional
    public ApInvoiceIngestion uploadAndEnqueue(MultipartFile file) {
        UUID tenantId = TenantContext.requireTenantId();
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "File is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "File exceeds limit of " + MAX_BYTES + " bytes");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "READ_FAILED", "Unable to read upload");
        }

        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "application/octet-stream";
        String ext = extensionFor(file.getOriginalFilename(), contentType);
        String storageKey = "tenants/" + tenantId + "/ap-ingestions/" + UUID.randomUUID() + ext;

        objectStorage.put(storageKey, bytes, contentType);

        ApInvoiceIngestion ingestion = new ApInvoiceIngestion();
        ingestion.setTenantId(tenantId);
        ingestion.setFileStorageKey(storageKey);
        ingestion.setIngestionStatus("PROCESSING");
        ingestion = ingestionRepository.save(ingestion);

        UUID ingestionId = ingestion.getId();
        virtualThreadExecutor.execute(() -> parseService.processIngestion(tenantId, ingestionId));
        return ingestion;
    }

    @Transactional(readOnly = true)
    public List<ApInvoiceIngestion> listForTenant() {
        return ingestionRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId());
    }

    @Transactional(readOnly = true)
    public ApInvoiceIngestion get(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        return ingestionRepository.findById(id)
                .filter(row -> tenantId.equals(row.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ingestion not found"));
    }

    private static String extensionFor(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (ALLOWED_EXTENSIONS.contains(ext)) {
                return ext.equals(".jpeg") ? ".jpg" : ext;
            }
        }
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> ".pdf";
            case "text/csv" -> ".csv";
            case "text/plain" -> ".txt";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> ".bin";
        };
    }
}
