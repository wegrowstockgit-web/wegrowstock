package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.ProductMedia;
import com.invsys.domain.ProductVariant;
import com.invsys.integration.OutboxService;
import com.invsys.media.MediaUrlValidator;
import com.invsys.repository.ProductMediaRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductMediaService {

    private final ProductMediaRepository mediaRepository;
    private final ProductVariantRepository variantRepository;
    private final OutboxService outboxService;
    private final MediaUrlValidator mediaUrlValidator;

    public ProductMediaService(ProductMediaRepository mediaRepository,
                               ProductVariantRepository variantRepository,
                               OutboxService outboxService,
                               MediaUrlValidator mediaUrlValidator) {
        this.mediaRepository = mediaRepository;
        this.variantRepository = variantRepository;
        this.outboxService = outboxService;
        this.mediaUrlValidator = mediaUrlValidator;
    }

    @Transactional
    public ProductMedia attach(UUID variantId, String url, boolean primary, Integer sortOrder) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
        if (!tenantId.equals(variant.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found");
        }
        String normalized = mediaUrlValidator.validateAndNormalize(url);

        boolean makePrimary = primary || mediaRepository
                .findFirstByTenantIdAndVariantIdAndPrimaryTrue(tenantId, variantId).isEmpty();
        if (makePrimary) {
            mediaRepository.clearPrimary(tenantId, variantId);
        }

        ProductMedia media = new ProductMedia();
        media.setTenantId(tenantId);
        media.setVariantId(variantId);
        media.setUrl(normalized);
        media.setPrimary(makePrimary);
        media.setSortOrder(sortOrder != null ? sortOrder : 0);
        media = mediaRepository.save(media);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("variantId", variantId.toString());
        payload.put("mediaId", media.getId().toString());
        payload.put("url", media.getUrl());
        payload.put("isPrimary", media.isPrimary());
        payload.put("sku", variant.getSku());
        // Catalog media sync pipeline (Shopify GraphQL on virtual-thread outbox workers)
        outboxService.append("PRODUCT_VARIANT", variantId, "PRODUCT_MEDIA_UPDATED", payload);

        return media;
    }

    @Transactional
    public ProductMedia setPrimary(UUID variantId, UUID mediaId) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
        if (!tenantId.equals(variant.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found");
        }
        mediaRepository.findByTenantIdAndIdAndVariantId(tenantId, mediaId, variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Media not found"));

        mediaRepository.clearPrimary(tenantId, variantId);
        // clearPrimary clears the persistence context — reload before mutating
        ProductMedia media = mediaRepository.findByTenantIdAndIdAndVariantId(tenantId, mediaId, variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Media not found"));
        media.setPrimary(true);
        media = mediaRepository.save(media);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("variantId", variantId.toString());
        payload.put("mediaId", media.getId().toString());
        payload.put("url", media.getUrl());
        payload.put("isPrimary", true);
        payload.put("sku", variant.getSku());
        outboxService.append("PRODUCT_VARIANT", variantId, "PRODUCT_MEDIA_UPDATED", payload);
        return media;
    }

    @Transactional(readOnly = true)
    public List<ProductMedia> listForVariant(UUID variantId) {
        return mediaRepository.findByTenantIdAndVariantIdOrderBySortOrderAscCreatedAtAsc(
                TenantContext.requireTenantId(), variantId);
    }

    @Transactional(readOnly = true)
    public String primaryUrl(UUID variantId) {
        return mediaRepository
                .findFirstByTenantIdAndVariantIdAndPrimaryTrue(TenantContext.requireTenantId(), variantId)
                .map(ProductMedia::getUrl)
                .orElse(null);
    }
}
