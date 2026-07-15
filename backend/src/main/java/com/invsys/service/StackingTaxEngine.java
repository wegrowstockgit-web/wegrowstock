package com.invsys.service;

import com.invsys.domain.TaxScheme;
import com.invsys.domain.TaxSchemeRate;
import com.invsys.repository.TaxSchemeRateRepository;
import com.invsys.repository.TaxSchemeRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stacking tax: Total_Tax = Σ (P × Q × Rate_i) with minor-unit HALF_UP rounding per line.
 */
@Service
public class StackingTaxEngine {

    private final TaxSchemeRepository schemeRepository;
    private final TaxSchemeRateRepository rateRepository;

    public StackingTaxEngine(TaxSchemeRepository schemeRepository,
                             TaxSchemeRateRepository rateRepository) {
        this.schemeRepository = schemeRepository;
        this.rateRepository = rateRepository;
    }

    @Transactional(readOnly = true)
    public TaxComputation compute(UUID schemeId, BigDecimal unitPrice, BigDecimal quantity) {
        UUID tenantId = TenantContext.requireTenantId();
        TaxScheme scheme = schemeId != null
                ? schemeRepository.findByTenantIdAndId(tenantId, schemeId).orElse(null)
                : schemeRepository.findFirstByTenantIdAndActiveTrueOrderByCreatedAtAsc(tenantId).orElse(null);
        if (scheme == null) {
            return TaxComputation.empty(unitPrice, quantity);
        }
        List<TaxSchemeRate> rates = rateRepository
                .findByTenantIdAndTaxSchemeIdOrderBySortOrderAsc(tenantId, scheme.getId());
        return compute(scheme, rates, unitPrice, quantity);
    }

    public TaxComputation compute(TaxScheme scheme,
                                  List<TaxSchemeRate> rates,
                                  BigDecimal unitPrice,
                                  BigDecimal quantity) {
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ONE;
        BigDecimal base = price.multiply(qty);
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal totalTax = BigDecimal.ZERO;
        for (TaxSchemeRate rate : rates) {
            BigDecimal lineTax = base.multiply(rate.getRate()).setScale(2, RoundingMode.HALF_UP);
            totalTax = totalTax.add(lineTax);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("name", rate.getName());
            line.put("rate", rate.getRate());
            line.put("amount", lineTax);
            line.put("sortOrder", rate.getSortOrder());
            lines.add(line);
        }
        BigDecimal taxableBase = scheme.isTaxInclusive()
                ? base.subtract(totalTax).max(BigDecimal.ZERO)
                : base;
        BigDecimal grandTotal = scheme.isTaxInclusive() ? base : base.add(totalTax);
        return new TaxComputation(
                scheme.getId(),
                scheme.getName(),
                scheme.isTaxInclusive(),
                price,
                qty,
                taxableBase.setScale(2, RoundingMode.HALF_UP),
                totalTax.setScale(2, RoundingMode.HALF_UP),
                grandTotal.setScale(2, RoundingMode.HALF_UP),
                lines);
    }

    public record TaxComputation(
            UUID schemeId,
            String schemeName,
            boolean taxInclusive,
            BigDecimal unitPrice,
            BigDecimal quantity,
            BigDecimal taxableBase,
            BigDecimal totalTax,
            BigDecimal grandTotal,
            List<Map<String, Object>> lines
    ) {
        static TaxComputation empty(BigDecimal unitPrice, BigDecimal quantity) {
            BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
            BigDecimal qty = quantity != null ? quantity : BigDecimal.ONE;
            BigDecimal base = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);
            return new TaxComputation(null, null, false, price, qty, base, BigDecimal.ZERO.setScale(2),
                    base, List.of());
        }
    }
}
