package com.invsys.repository;

import com.invsys.domain.VariantUomConversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VariantUomConversionRepository extends JpaRepository<VariantUomConversion, UUID> {
    List<VariantUomConversion> findByTenantIdAndVariantId(UUID tenantId, UUID variantId);

    Optional<VariantUomConversion> findByTenantIdAndVariantIdAndUomType(
            UUID tenantId, UUID variantId, String uomType);
}
