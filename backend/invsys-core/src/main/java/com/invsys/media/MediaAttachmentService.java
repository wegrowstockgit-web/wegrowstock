package com.invsys.media;

import com.invsys.core.common.ApiException;
import com.invsys.domain.MediaAttachment;
import com.invsys.domain.MediaObject;
import com.invsys.domain.User;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.MediaAttachmentRepository;
import com.invsys.repository.MediaObjectRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.ProductionOrderRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.repository.ReturnLineRepository;
import com.invsys.repository.UserRepository;
import com.invsys.service.ProductMediaService;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaAttachmentService {

    private static final Set<String> ENTITY_TYPES = Set.of(
            "USER", "PRODUCT_VARIANT", "RETURN_LINE", "PURCHASE_ORDER_LINE",
            "LOCATION", "PRODUCTION_ORDER");

    private static final Set<String> PURPOSES = Set.of(
            "AVATAR", "PRIMARY", "GALLERY", "QC_DAMAGE", "RETURN_CONDITION",
            "LOCATION", "RECEIVE_EVIDENCE");

    private final MediaAttachmentRepository attachmentRepository;
    private final MediaObjectRepository mediaObjectRepository;
    private final MediaUploadService uploadService;
    private final ProductMediaService productMediaService;
    private final UserRepository userRepository;
    private final ProductVariantRepository variantRepository;
    private final ReturnLineRepository returnLineRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final LocationRepository locationRepository;
    private final ProductionOrderRepository productionOrderRepository;

    public MediaAttachmentService(MediaAttachmentRepository attachmentRepository,
                                  MediaObjectRepository mediaObjectRepository,
                                  MediaUploadService uploadService,
                                  ProductMediaService productMediaService,
                                  UserRepository userRepository,
                                  ProductVariantRepository variantRepository,
                                  ReturnLineRepository returnLineRepository,
                                  PurchaseOrderLineRepository purchaseOrderLineRepository,
                                  LocationRepository locationRepository,
                                  ProductionOrderRepository productionOrderRepository) {
        this.attachmentRepository = attachmentRepository;
        this.mediaObjectRepository = mediaObjectRepository;
        this.uploadService = uploadService;
        this.productMediaService = productMediaService;
        this.userRepository = userRepository;
        this.variantRepository = variantRepository;
        this.returnLineRepository = returnLineRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.locationRepository = locationRepository;
        this.productionOrderRepository = productionOrderRepository;
    }

    @Transactional
    public MediaAttachment attach(UUID mediaObjectId, String entityType, UUID entityId, String purpose, Integer sortOrder) {
        UUID tenantId = TenantContext.requireTenantId();
        String type = normalize(entityType, ENTITY_TYPES, "INVALID_ENTITY_TYPE");
        String use = normalize(purpose, PURPOSES, "INVALID_PURPOSE");
        MediaObject media = mediaObjectRepository.findByTenantIdAndId(tenantId, mediaObjectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Media not found"));
        assertEntityExists(tenantId, type, entityId);

        MediaAttachment attachment = new MediaAttachment();
        attachment.setTenantId(tenantId);
        attachment.setMediaObjectId(media.getId());
        attachment.setEntityType(type);
        attachment.setEntityId(entityId);
        attachment.setPurpose(use);
        attachment.setSortOrder(sortOrder != null ? sortOrder : 0);
        attachment.setCreatedBy(TenantContext.getUserId().orElse(null));
        attachment = attachmentRepository.save(attachment);

        String contentUrl = uploadService.contentPath(media.getId());
        if ("USER".equals(type) && "AVATAR".equals(use)) {
            User user = userRepository.findById(entityId).orElseThrow();
            user.setAvatarUrl(contentUrl);
            userRepository.save(user);
        } else if ("PRODUCT_VARIANT".equals(type) && ("PRIMARY".equals(use) || "GALLERY".equals(use))) {
            productMediaService.attach(entityId, contentUrl, "PRIMARY".equals(use), sortOrder);
        }
        return attachment;
    }

    @Transactional(readOnly = true)
    public List<MediaAttachment> list(String entityType, UUID entityId) {
        UUID tenantId = TenantContext.requireTenantId();
        String type = normalize(entityType, ENTITY_TYPES, "INVALID_ENTITY_TYPE");
        return attachmentRepository
                .findByTenantIdAndEntityTypeAndEntityIdOrderBySortOrderAscCreatedAtAsc(tenantId, type, entityId);
    }

    private void assertEntityExists(UUID tenantId, String type, UUID entityId) {
        boolean ok = switch (type) {
            case "USER" -> userRepository.findById(entityId)
                    .filter(u -> tenantId.equals(u.getTenantId())).isPresent();
            case "PRODUCT_VARIANT" -> variantRepository.findById(entityId)
                    .filter(v -> tenantId.equals(v.getTenantId())).isPresent();
            case "RETURN_LINE" -> returnLineRepository.findById(entityId)
                    .filter(l -> tenantId.equals(l.getTenantId())).isPresent();
            case "PURCHASE_ORDER_LINE" -> purchaseOrderLineRepository.findById(entityId)
                    .filter(l -> tenantId.equals(l.getTenantId())).isPresent();
            case "LOCATION" -> locationRepository.findById(entityId)
                    .filter(l -> tenantId.equals(l.getTenantId())).isPresent();
            case "PRODUCTION_ORDER" -> productionOrderRepository.findById(entityId)
                    .filter(o -> tenantId.equals(o.getTenantId())).isPresent();
            default -> false;
        };
        if (!ok) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Entity not found for attachment");
        }
    }

    private static String normalize(String value, Set<String> allowed, String code) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Value is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, "Unsupported value: " + value);
        }
        return normalized;
    }
}
