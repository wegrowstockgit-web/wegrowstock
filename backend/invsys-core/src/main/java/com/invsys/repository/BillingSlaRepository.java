package com.invsys.repository;

import com.invsys.domain.BillingSla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingSlaRepository extends JpaRepository<BillingSla, UUID> {
    List<BillingSla> findByTenantId(UUID tenantId);

    Optional<BillingSla> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
