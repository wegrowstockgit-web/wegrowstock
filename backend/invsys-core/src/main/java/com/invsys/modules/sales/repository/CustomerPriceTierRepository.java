package com.invsys.modules.sales.repository;

import com.invsys.domain.CustomerPriceTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerPriceTierRepository extends JpaRepository<CustomerPriceTier, UUID> {
    List<CustomerPriceTier> findByTenantIdOrderByNameAsc(UUID tenantId);
}
