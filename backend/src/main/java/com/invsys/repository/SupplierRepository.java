package com.invsys.repository;

import com.invsys.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findByTenantIdOrderByNameAsc(UUID tenantId);
}
