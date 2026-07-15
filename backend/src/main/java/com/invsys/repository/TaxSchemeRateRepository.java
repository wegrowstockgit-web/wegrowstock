package com.invsys.repository;

import com.invsys.domain.TaxSchemeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaxSchemeRateRepository extends JpaRepository<TaxSchemeRate, UUID> {
    List<TaxSchemeRate> findByTenantIdAndTaxSchemeIdOrderBySortOrderAsc(UUID tenantId, UUID taxSchemeId);

    void deleteByTenantIdAndTaxSchemeId(UUID tenantId, UUID taxSchemeId);
}
