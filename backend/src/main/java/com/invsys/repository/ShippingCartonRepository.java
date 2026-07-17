package com.invsys.repository;

import com.invsys.domain.ShippingCarton;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShippingCartonRepository extends JpaRepository<ShippingCarton, UUID> {
    List<ShippingCarton> findByTenantIdAndActiveTrueOrderByLengthAscWidthAscHeightAsc(UUID tenantId);
}
