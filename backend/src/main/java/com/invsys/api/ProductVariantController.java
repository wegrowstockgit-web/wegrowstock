package com.invsys.api;



import com.invsys.api.dto.VariantListItemResponse;

import com.invsys.common.ApiException;

import com.invsys.common.PageResponse;

import com.invsys.domain.ProductVariant;

import com.invsys.repository.ProductVariantRepository;

import com.invsys.service.UomConversionService;

import com.invsys.service.VariantCatalogService;

import com.invsys.tenancy.TenantContext;

import jakarta.validation.Valid;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PatchMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;



import java.math.BigDecimal;

import java.util.List;

import java.util.Map;

import java.util.UUID;



@RestController

@RequestMapping("/api/v1/variants")

public class ProductVariantController {



    private final ProductVariantRepository variantRepository;

    private final VariantCatalogService variantCatalogService;

    private final UomConversionService uomConversionService;



    public ProductVariantController(ProductVariantRepository variantRepository,

                                    VariantCatalogService variantCatalogService,

                                    UomConversionService uomConversionService) {

        this.variantRepository = variantRepository;

        this.variantCatalogService = variantCatalogService;

        this.uomConversionService = uomConversionService;

    }



    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public PageResponse<VariantListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return variantCatalogService.list(q, cursor, limit);
    }

    @GetMapping("/product/{productId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<ProductVariant> byProduct(@PathVariable UUID productId) {
        return variantRepository.findByTenantIdAndProductId(TenantContext.requireTenantId(), productId);
    }



    @PostMapping

    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")

    public ProductVariant create(@Valid @RequestBody CreateVariantRequest request) {

        ProductVariant variant = new ProductVariant();

        variant.setTenantId(TenantContext.requireTenantId());

        variant.setProductId(request.productId());

        variant.setSku(request.sku());

        variant.setBarcode(request.barcode());

        variant.setAttributes(request.attributes() != null ? request.attributes() : Map.of());

        variant.setPrice(request.price() != null ? request.price() : BigDecimal.ZERO);

        variant.setCurrency(request.currency() != null ? request.currency() : "USD");

        variant = variantRepository.save(variant);

        uomConversionService.saveForVariant(variant.getId(), List.of(

                new UomConversionService.UomConversionRequest("STANDARD", "EA", BigDecimal.ONE)));

        return variant;

    }



    @PatchMapping("/{id}")

    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")

    public ProductVariant patch(@PathVariable UUID id, @Valid @RequestBody PatchVariantRequest request) {

        ProductVariant variant = variantRepository.findById(id)

                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));

        if (request.externalSyncEnabled() != null) {

            variant.setExternalSyncEnabled(request.externalSyncEnabled());

        }

        if (request.defaultLocationId() != null) {

            variant.setDefaultLocationId(request.defaultLocationId());

        }

        if (request.isKit() != null) {

            variant.setKit(request.isKit());

        }

        if (request.dims() != null) {

            variant.setDims(request.dims());

        }

        if (request.reorderPoint() != null) {

            variant.setReorderPoint(request.reorderPoint());

        }

        if (request.reorderQty() != null) {

            variant.setReorderQty(request.reorderQty());

        }

        return variantRepository.save(variant);

    }



    public record CreateVariantRequest(

            @NotNull UUID productId,

            @NotBlank String sku,

            String barcode,

            Map<String, Object> attributes,

            BigDecimal price,

            String currency

    ) {

    }



    public record PatchVariantRequest(Boolean externalSyncEnabled, UUID defaultLocationId, Boolean isKit,

                                      Map<String, Object> dims, BigDecimal reorderPoint, BigDecimal reorderQty) {

    }

}


