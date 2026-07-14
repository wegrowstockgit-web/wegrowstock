package com.invsys.api;

import com.invsys.domain.ProductMedia;
import com.invsys.service.ProductMediaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/variants")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class ProductMediaController {

    private final ProductMediaService productMediaService;

    public ProductMediaController(ProductMediaService productMediaService) {
        this.productMediaService = productMediaService;
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
