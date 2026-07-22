package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.TransactionMedia;
import com.invsys.media.MediaUrlValidator;
import com.invsys.repository.TransactionMediaRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class TransactionMediaService {

    private static final Set<String> ENTITY_TYPES = Set.of(
            "RECEIPT", "RMA", "LEDGER_ENTRY", "PURCHASE_ORDER_LINE", "RETURN_LINE", "FULFILLMENT_SCAN");

    private final TransactionMediaRepository repository;
    private final MediaUrlValidator mediaUrlValidator;

    public TransactionMediaService(TransactionMediaRepository repository, MediaUrlValidator mediaUrlValidator) {
        this.repository = repository;
        this.mediaUrlValidator = mediaUrlValidator;
    }

    @Transactional
    public TransactionMedia attach(String entityType, UUID entityId, String url) {
        UUID tenantId = TenantContext.requireTenantId();
        String type = normalize(entityType);
        String normalizedUrl = mediaUrlValidator.validateAndNormalize(url);

        TransactionMedia media = new TransactionMedia();
        media.setTenantId(tenantId);
        media.setEntityType(type);
        media.setEntityId(entityId);
        media.setUrl(normalizedUrl);
        media.setCapturedBy(TenantContext.getUserId().orElse(null));
        return repository.save(media);
    }

    @Transactional(readOnly = true)
    public List<TransactionMedia> list(String entityType, UUID entityId) {
        return repository.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                TenantContext.requireTenantId(), normalize(entityType), entityId);
    }

    private static String normalize(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ENTITY_TYPE", "entityType is required");
        }
        String normalized = entityType.trim().toUpperCase(Locale.ROOT);
        if (!ENTITY_TYPES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ENTITY_TYPE",
                    "Unsupported transaction media entity type");
        }
        return normalized;
    }
}
