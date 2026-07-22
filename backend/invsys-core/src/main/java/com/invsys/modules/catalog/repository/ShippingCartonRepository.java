package com.invsys.modules.catalog.repository;

import com.invsys.modules.catalog.domain.ShippingCarton;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShippingCartonRepository extends JpaRepository<ShippingCarton, UUID> {
    List<ShippingCarton> findByTenantIdAndActiveTrueOrderByLengthAscWidthAscHeightAsc(UUID tenantId);
}
