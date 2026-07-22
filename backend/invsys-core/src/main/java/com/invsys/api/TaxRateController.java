package com.invsys.api;

import com.invsys.domain.TaxRate;
import com.invsys.service.TaxService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/taxes")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class TaxRateController {

    private final TaxService taxService;

    public TaxRateController(TaxService taxService) {
        this.taxService = taxService;
    }

    @GetMapping
    public List<TaxRateResponse> list() {
        return taxService.list().stream()
                .map(TaxRateResponse::from)
                .toList();
    }

    @PostMapping
    public TaxRateResponse create(@Valid @RequestBody CreateTaxRateRequest request) {
        return TaxRateResponse.from(taxService.create(request.name(), request.rate(), request.isDefault()));
    }

    @PatchMapping("/{id}")
    public TaxRateResponse update(@PathVariable UUID id, @RequestBody UpdateTaxRateRequest request) {
        return TaxRateResponse.from(taxService.update(id, request.name(), request.rate(), request.isDefault()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        taxService.delete(id);
    }

    public record CreateTaxRateRequest(
            @NotBlank String name,
            @NotNull BigDecimal rate,
            boolean isDefault
    ) {
    }

    public record UpdateTaxRateRequest(String name, BigDecimal rate, Boolean isDefault) {
    }

    public record TaxRateResponse(UUID id, String name, BigDecimal rate, boolean isDefault) {
        static TaxRateResponse from(TaxRate rate) {
            return new TaxRateResponse(rate.getId(), rate.getName(), rate.getRate(), rate.isDefaultRate());
        }
    }
}
