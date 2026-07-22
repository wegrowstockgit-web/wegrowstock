package com.invsys.modules.catalog.repository;

import com.invsys.modules.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(UUID tenantId);

    Optional<Product> findByTenantIdAndSkuRoot(UUID tenantId, String skuRoot);
}
