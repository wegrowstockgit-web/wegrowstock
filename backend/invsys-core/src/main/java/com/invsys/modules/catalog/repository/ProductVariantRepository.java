package com.invsys.modules.catalog.repository;

import com.invsys.modules.catalog.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByTenantIdAndProductId(UUID tenantId, UUID productId);
    Optional<ProductVariant> findByTenantIdAndBarcode(UUID tenantId, String barcode);
    Optional<ProductVariant> findByTenantIdAndSku(UUID tenantId, String sku);
    List<ProductVariant> findByTenantIdAndLifecycleStatus(UUID tenantId, String lifecycleStatus);
}
