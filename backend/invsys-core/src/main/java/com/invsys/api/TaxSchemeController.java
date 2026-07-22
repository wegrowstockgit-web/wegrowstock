package com.invsys.api;

import com.invsys.service.StackingTaxEngine;
import com.invsys.service.TaxSchemeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/tax-schemes")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class TaxSchemeController {

    private final TaxSchemeService taxSchemeService;

    public TaxSchemeController(TaxSchemeService taxSchemeService) {
        this.taxSchemeService = taxSchemeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public List<TaxSchemeService.SchemeView> list() {
        return taxSchemeService.list();
    }

    @PostMapping
    public TaxSchemeService.SchemeView create(@Valid @RequestBody CreateSchemeRequest request) {
        List<TaxSchemeService.RateInput> rates = request.rates() == null ? List.of() : request.rates().stream()
                .map(r -> new TaxSchemeService.RateInput(r.name(), r.rate(), r.sortOrder()))
                .toList();
        return taxSchemeService.create(request.name(), request.taxInclusive(), rates);
    }

    @PutMapping("/{id}")
    public TaxSchemeService.SchemeView update(@PathVariable UUID id, @Valid @RequestBody UpdateSchemeRequest request) {
        List<TaxSchemeService.RateInput> rates = request.rates() == null ? null : request.rates().stream()
                .map(r -> new TaxSchemeService.RateInput(r.name(), r.rate(), r.sortOrder()))
                .toList();
        return taxSchemeService.update(id, request.name(), request.taxInclusive(), request.active(), rates);
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public StackingTaxEngine.TaxComputation preview(@Valid @RequestBody PreviewRequest request) {
        return taxSchemeService.preview(request.schemeId(), request.unitPrice(), request.quantity());
    }

    public record RateBody(@NotBlank String name, @NotNull @PositiveOrZero BigDecimal rate, Integer sortOrder) {
    }

    public record CreateSchemeRequest(
            @NotBlank String name,
            boolean taxInclusive,
            List<RateBody> rates
    ) {
    }

    public record UpdateSchemeRequest(
            String name,
            Boolean taxInclusive,
            Boolean active,
            List<RateBody> rates
    ) {
    }

    public record PreviewRequest(
            UUID schemeId,
            @NotNull @PositiveOrZero BigDecimal unitPrice,
            @NotNull @PositiveOrZero BigDecimal quantity
    ) {
    }
}
