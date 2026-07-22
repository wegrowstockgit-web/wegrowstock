package com.invsys.api;

import com.invsys.domain.VariantUomConversion;
import com.invsys.service.UomConversionService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/variants/{variantId}/uom")
public class VariantUomController {

    private final UomConversionService uomConversionService;

    public VariantUomController(UomConversionService uomConversionService) {
        this.uomConversionService = uomConversionService;
    }

    @GetMapping
    public List<VariantUomConversion> list(@PathVariable UUID variantId) {
        TenantContext.requireTenantId();
        return uomConversionService.listForVariant(variantId);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public List<VariantUomConversion> save(@PathVariable UUID variantId,
                                           @Valid @RequestBody List<UomConversionRequest> conversions) {
        List<UomConversionService.UomConversionRequest> mapped = conversions.stream()
                .map(c -> new UomConversionService.UomConversionRequest(
                        c.uomType(), c.unitName(), c.conversionRatio()))
                .toList();
        return uomConversionService.saveForVariant(variantId, mapped);
    }

    public record UomConversionRequest(
            @NotBlank String uomType,
            @NotBlank String unitName,
            @NotNull BigDecimal conversionRatio
    ) {
    }
}
