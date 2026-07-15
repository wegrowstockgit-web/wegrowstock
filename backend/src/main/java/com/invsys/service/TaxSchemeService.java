package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.TaxScheme;
import com.invsys.domain.TaxSchemeRate;
import com.invsys.repository.TaxSchemeRateRepository;
import com.invsys.repository.TaxSchemeRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TaxSchemeService {

    private final TaxSchemeRepository schemeRepository;
    private final TaxSchemeRateRepository rateRepository;
    private final StackingTaxEngine stackingTaxEngine;

    public TaxSchemeService(TaxSchemeRepository schemeRepository,
                            TaxSchemeRateRepository rateRepository,
                            StackingTaxEngine stackingTaxEngine) {
        this.schemeRepository = schemeRepository;
        this.rateRepository = rateRepository;
        this.stackingTaxEngine = stackingTaxEngine;
    }

    @Transactional(readOnly = true)
    public List<SchemeView> list() {
        UUID tenantId = TenantContext.requireTenantId();
        List<SchemeView> views = new ArrayList<>();
        for (TaxScheme scheme : schemeRepository.findByTenantIdOrderByNameAsc(tenantId)) {
            views.add(toView(scheme));
        }
        return views;
    }

    @Transactional
    public SchemeView create(String name, boolean taxInclusive, List<RateInput> rates) {
        UUID tenantId = TenantContext.requireTenantId();
        TaxScheme scheme = new TaxScheme();
        scheme.setTenantId(tenantId);
        scheme.setName(name.trim());
        scheme.setTaxInclusive(taxInclusive);
        scheme.setActive(true);
        scheme = schemeRepository.save(scheme);
        replaceRates(tenantId, scheme.getId(), rates);
        return toView(scheme);
    }

    @Transactional
    public SchemeView update(UUID id, String name, Boolean taxInclusive, Boolean active, List<RateInput> rates) {
        UUID tenantId = TenantContext.requireTenantId();
        TaxScheme scheme = schemeRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tax scheme not found"));
        if (name != null && !name.isBlank()) {
            scheme.setName(name.trim());
        }
        if (taxInclusive != null) {
            scheme.setTaxInclusive(taxInclusive);
        }
        if (active != null) {
            scheme.setActive(active);
        }
        schemeRepository.save(scheme);
        if (rates != null) {
            replaceRates(tenantId, scheme.getId(), rates);
        }
        return toView(scheme);
    }

    @Transactional(readOnly = true)
    public StackingTaxEngine.TaxComputation preview(UUID schemeId, BigDecimal unitPrice, BigDecimal quantity) {
        return stackingTaxEngine.compute(schemeId, unitPrice, quantity);
    }

    private void replaceRates(UUID tenantId, UUID schemeId, List<RateInput> rates) {
        rateRepository.deleteByTenantIdAndTaxSchemeId(tenantId, schemeId);
        if (rates == null) {
            return;
        }
        int order = 0;
        for (RateInput input : rates) {
            TaxSchemeRate rate = new TaxSchemeRate();
            rate.setTenantId(tenantId);
            rate.setTaxSchemeId(schemeId);
            rate.setName(input.name());
            rate.setRate(input.rate());
            rate.setSortOrder(input.sortOrder() != null ? input.sortOrder() : order++);
            rateRepository.save(rate);
        }
    }

    private SchemeView toView(TaxScheme scheme) {
        List<TaxSchemeRate> rates = rateRepository
                .findByTenantIdAndTaxSchemeIdOrderBySortOrderAsc(scheme.getTenantId(), scheme.getId());
        List<RateView> rateViews = rates.stream()
                .map(r -> new RateView(r.getId(), r.getName(), r.getRate(), r.getSortOrder()))
                .toList();
        return new SchemeView(scheme.getId(), scheme.getName(), scheme.isTaxInclusive(),
                scheme.isActive(), rateViews);
    }

    public record RateInput(String name, BigDecimal rate, Integer sortOrder) {
    }

    public record RateView(UUID id, String name, BigDecimal rate, int sortOrder) {
    }

    public record SchemeView(
            UUID id,
            String name,
            boolean taxInclusive,
            boolean active,
            List<RateView> rates
    ) {
    }
}
