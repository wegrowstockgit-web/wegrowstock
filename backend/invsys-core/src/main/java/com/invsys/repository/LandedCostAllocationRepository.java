package com.invsys.repository;

import com.invsys.domain.LandedCostAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LandedCostAllocationRepository extends JpaRepository<LandedCostAllocation, UUID> {
    List<LandedCostAllocation> findByTenantIdAndSupplierInvoiceIdOrderByCreatedAtDesc(
            UUID tenantId, UUID supplierInvoiceId);
}
