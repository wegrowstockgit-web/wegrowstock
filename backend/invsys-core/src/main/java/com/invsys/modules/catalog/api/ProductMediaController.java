package com.invsys.modules.catalog.api;

import com.invsys.domain.MediaObject;
import com.invsys.domain.ProductMedia;
import com.invsys.media.MediaAttachmentService;
import com.invsys.media.MediaUploadService;
import com.invsys.service.ProductMediaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/variants")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class ProductMediaController {

    private final ProductMediaService productMediaService;
    private final MediaUploadService mediaUploadService;
    private final MediaAttachmentService mediaAttachmentService;

    public ProductMediaController(ProductMediaService productMediaService,
                                  MediaUploadService mediaUploadService,
                                  MediaAttachmentService mediaAttachmentService) {
        this.productMediaService = productMediaService;
        this.mediaUploadService = mediaUploadService;
        this.mediaAttachmentService = mediaAttachmentService;
    }

    @PostMapping("/{id}/media")
    public MediaResponse attach(@PathVariable UUID id, @Valid @RequestBody MediaRequest request) {
        ProductMedia media = productMediaService.attach(
                id,
                request.url(),
                request.isPrimary() != null && request.isPrimary(),
                request.sortOrder());
        return toResponse(media);
    }

    @PostMapping(value = "/{id}/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaResponse upload(@PathVariable UUID id,
                                @RequestPart("file") MultipartFile file,
                                @RequestParam(defaultValue = "true") boolean primary) {
        MediaObject uploaded = mediaUploadService.upload(file, MediaUploadService.UploadKind.PRODUCT);
        mediaAttachmentService.attach(
                uploaded.getId(),
                "PRODUCT_VARIANT",
                id,
                primary ? "PRIMARY" : "GALLERY",
                0);
        String url = mediaUploadService.contentPath(uploaded.getId());
        ProductMedia media = productMediaService.listForVariant(id).stream()
                .filter(m -> url.equals(m.getUrl()))
                .findFirst()
                .orElseThrow();
        return toResponse(media);
    }

    @PutMapping("/{id}/media/{mediaId}/primary")
    public MediaResponse setPrimary(@PathVariable UUID id, @PathVariable UUID mediaId) {
        return toResponse(productMediaService.setPrimary(id, mediaId));
    }

    @GetMapping("/{id}/media")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<MediaResponse> list(@PathVariable UUID id) {
        return productMediaService.listForVariant(id).stream().map(this::toResponse).toList();
    }

    private MediaResponse toResponse(ProductMedia media) {
        return new MediaResponse(
                media.getId(),
                media.getVariantId(),
                media.getUrl(),
                media.isPrimary(),
                media.getSortOrder());
    }

    public record MediaRequest(
            @NotBlank @Size(max = 1024) String url,
            Boolean isPrimary,
            Integer sortOrder
    ) {
    }

    public record MediaResponse(
            UUID id,
            UUID variantId,
            String url,
            boolean isPrimary,
            int sortOrder
    ) {
    }
}
