package com.invsys.repository;

import com.invsys.domain.DemandForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandForecastRepository extends JpaRepository<DemandForecast, UUID> {
    List<DemandForecast> findByTenantIdOrderByRecommendedPoQtyDesc(UUID tenantId);

    Optional<DemandForecast> findByTenantIdAndVariantId(UUID tenantId, UUID variantId);
}
