package com.invsys.repository;

import com.invsys.domain.VolumePriceBreak;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VolumePriceBreakRepository extends JpaRepository<VolumePriceBreak, UUID> {
    List<VolumePriceBreak> findByTenantIdAndVariantIdOrderByMinQuantityAsc(UUID tenantId, UUID variantId);

    Optional<VolumePriceBreak> findFirstByTenantIdAndVariantIdAndMinQuantityLessThanEqualOrderByMinQuantityDesc(
            UUID tenantId, UUID variantId, BigDecimal minQuantity);
}
