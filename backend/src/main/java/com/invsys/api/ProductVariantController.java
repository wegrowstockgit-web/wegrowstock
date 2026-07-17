package com.invsys.api;

import com.invsys.api.dto.VariantListItemResponse;
import com.invsys.common.ApiException;
import com.invsys.common.PageResponse;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.VariantBarcode;
import com.invsys.integration.OutboxService;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.VariantBarcodeRepository;
import com.invsys.service.SkuMaskService;
import com.invsys.service.UomConversionService;
import com.invsys.service.VariantCatalogService;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final VariantBarcodeRepository variantBarcodeRepository;
    private final SkuMaskService skuMaskService;
    private final OutboxService outboxService;

    public ProductVariantController(ProductVariantRepository variantRepository,
                                    VariantCatalogService variantCatalogService,
                                    UomConversionService uomConversionService,
                                    VariantBarcodeRepository variantBarcodeRepository,
                                    SkuMaskService skuMaskService,
                                    OutboxService outboxService) {
        this.variantRepository = variantRepository;
        this.outboxService = outboxService;
        this.variantCatalogService = variantCatalogService;
        this.uomConversionService = uomConversionService;
        this.variantBarcodeRepository = variantBarcodeRepository;
        this.skuMaskService = skuMaskService;
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
    @Transactional
    public ProductVariant create(@Valid @RequestBody CreateVariantRequest request) {
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(TenantContext.requireTenantId());
        variant.setProductId(request.productId());
        String skuTemplate = request.skuTemplate();
        variant.setSkuTemplate(skuTemplate);
        variant.setSku(skuMaskService.mintSku(request.sku(), skuTemplate));
        String barcode = request.barcode() != null && !request.barcode().isBlank()
                ? request.barcode().trim()
                : null;
        variant.setBarcode(barcode);
        variant.setAttributes(request.attributes() != null ? request.attributes() : Map.of());
        variant.setPrice(request.price() != null ? request.price() : BigDecimal.ZERO);
        variant.setCurrency(request.currency() != null ? request.currency() : "USD");
        applyDims(variant, request.weight(), request.weightUnit(), request.length(),
                request.width(), request.height(), request.dimUnit());
        variant = variantRepository.save(variant);
        uomConversionService.saveForVariant(variant.getId(), List.of(
                new UomConversionService.UomConversionRequest("STANDARD", "EA", BigDecimal.ONE)));
        if (barcode != null) {
            VariantBarcode primary = new VariantBarcode();
            primary.setTenantId(TenantContext.requireTenantId());
            primary.setVariantId(variant.getId());
            primary.setBarcode(barcode);
            primary.setSymbology("UPC");
            primary.setPrimary(true);
            variantBarcodeRepository.save(primary);
        }
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
        if (request.isLotTracked() != null) {
            variant.setLotTracked(request.isLotTracked());
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
        applyDims(variant, request.weight(), request.weightUnit(), request.length(),
                request.width(), request.height(), request.dimUnit());
        return variantRepository.save(variant);
    }

    @PatchMapping("/{id}/channel-sync")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    @Transactional
    public ProductVariant patchChannelSync(
            @PathVariable UUID id,
            @Valid @RequestBody ChannelSyncRequest request) {
        ProductVariant variant = requireVariant(id);
        boolean previous = variant.isExternalSyncEnabled();
        boolean next = Boolean.TRUE.equals(request.enabled());
        if (previous != next) {
            variant.setExternalSyncEnabled(next);
            variant = variantRepository.save(variant);
            outboxService.append(
                    "PRODUCT_VARIANT",
                    variant.getId(),
                    "CHANNEL_SYNC_TOGGLED",
                    Map.of(
                            "variantId", variant.getId(),
                            "externalSyncEnabled", next,
                            "sku", variant.getSku() != null ? variant.getSku() : ""));
        }
        return variant;
    }

    @GetMapping("/{id}/barcodes")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<VariantBarcode> listBarcodes(@PathVariable UUID id) {
        requireVariant(id);
        return variantBarcodeRepository.findByTenantIdAndVariantIdOrderByCreatedAtAsc(
                TenantContext.requireTenantId(), id);
    }

    @PostMapping("/{id}/barcodes")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public VariantBarcode addBarcode(@PathVariable UUID id, @Valid @RequestBody AddBarcodeRequest request) {
        requireVariant(id);
        variantBarcodeRepository.findByTenantIdAndBarcode(TenantContext.requireTenantId(), request.barcode().trim())
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "BARCODE_EXISTS", "Barcode already registered");
                });
        VariantBarcode barcode = new VariantBarcode();
        barcode.setTenantId(TenantContext.requireTenantId());
        barcode.setVariantId(id);
        barcode.setBarcode(request.barcode().trim());
        barcode.setSymbology(request.symbology() != null && !request.symbology().isBlank()
                ? request.symbology() : "OTHER");
        barcode.setPrimary(false);
        return variantBarcodeRepository.save(barcode);
    }

    @DeleteMapping("/{id}/barcodes/{barcodeId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public void deleteBarcode(@PathVariable UUID id, @PathVariable UUID barcodeId) {
        requireVariant(id);
        VariantBarcode barcode = variantBarcodeRepository
                .findByIdAndTenantIdAndVariantId(barcodeId, TenantContext.requireTenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Barcode not found"));
        variantBarcodeRepository.delete(barcode);
    }

    private ProductVariant requireVariant(UUID id) {
        return variantRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
    }

    private static void applyDims(ProductVariant variant,
                                  BigDecimal weight, String weightUnit,
                                  BigDecimal length, BigDecimal width, BigDecimal height,
                                  String dimUnit) {
        if (weight != null) {
            variant.setWeight(weight);
        }
        if (weightUnit != null && !weightUnit.isBlank()) {
            variant.setWeightUnit(weightUnit);
        }
        if (length != null) {
            variant.setLength(length);
        }
        if (width != null) {
            variant.setWidth(width);
        }
        if (height != null) {
            variant.setHeight(height);
        }
        if (dimUnit != null && !dimUnit.isBlank()) {
            variant.setDimUnit(dimUnit);
        }
    }

    public record CreateVariantRequest(
            @NotNull UUID productId,
            String sku,
            String skuTemplate,
            String barcode,
            Map<String, Object> attributes,
            BigDecimal price,
            String currency,
            BigDecimal weight,
            String weightUnit,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            String dimUnit
    ) {
    }

    public record PatchVariantRequest(
            Boolean externalSyncEnabled,
            UUID defaultLocationId,
            Boolean isKit,
            Boolean isLotTracked,
            Map<String, Object> dims,
            BigDecimal reorderPoint,
            BigDecimal reorderQty,
            BigDecimal weight,
            String weightUnit,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            String dimUnit
    ) {
    }

    public record AddBarcodeRequest(@NotBlank String barcode, String symbology) {
    }

    public record ChannelSyncRequest(@NotNull Boolean enabled) {
    }
}
