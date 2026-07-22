package com.invsys.modules.catalog.repository;

import com.invsys.domain.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    List<ProductCategory> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<ProductCategory> findByTenantIdAndId(UUID tenantId, UUID id);
}
