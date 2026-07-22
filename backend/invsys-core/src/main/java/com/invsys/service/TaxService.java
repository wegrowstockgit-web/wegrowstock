package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.TaxRate;
import com.invsys.repository.TaxRateRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaxService {

    private final TaxRateRepository taxRateRepository;

    public TaxService(TaxRateRepository taxRateRepository) {
        this.taxRateRepository = taxRateRepository;
    }

    public List<TaxRate> list() {
        return taxRateRepository.findByTenantIdOrderByNameAsc(TenantContext.requireTenantId());
    }

    @Transactional
    public TaxRate create(String name, BigDecimal rate, boolean isDefault) {
        UUID tenantId = TenantContext.requireTenantId();
        if (isDefault) {
            clearDefault(tenantId);
        }
        TaxRate taxRate = new TaxRate();
        taxRate.setTenantId(tenantId);
        taxRate.setName(name);
        taxRate.setRate(rate);
        taxRate.setDefaultRate(isDefault);
        return taxRateRepository.save(taxRate);
    }

    @Transactional
    public TaxRate update(UUID id, String name, BigDecimal rate, Boolean isDefault) {
        UUID tenantId = TenantContext.requireTenantId();
        TaxRate taxRate = taxRateRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tax rate not found"));
        if (name != null) {
            taxRate.setName(name);
        }
        if (rate != null) {
            taxRate.setRate(rate);
        }
        if (isDefault != null) {
            if (isDefault) {
                clearDefault(tenantId);
            }
            taxRate.setDefaultRate(isDefault);
        }
        return taxRateRepository.save(taxRate);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        TaxRate taxRate = taxRateRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Tax rate not found"));
        taxRateRepository.delete(taxRate);
    }

    public Map<String, Object> defaultTaxPayload() {
        return taxRateRepository.findByTenantIdAndDefaultRateTrue(TenantContext.requireTenantId())
                .map(rate -> {
                    Map<String, Object> tax = new LinkedHashMap<>();
                    tax.put("name", rate.getName());
                    tax.put("rate", rate.getRate());
                    return tax;
                })
                .orElseGet(Map::of);
    }

    private void clearDefault(UUID tenantId) {
        taxRateRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .filter(TaxRate::isDefaultRate)
                .forEach(rate -> {
                    rate.setDefaultRate(false);
                    taxRateRepository.save(rate);
                });
    }
}
