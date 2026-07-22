package com.invsys.modules.purchasing.repository;

import com.invsys.modules.purchasing.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findByTenantIdOrderByNameAsc(UUID tenantId);
}
