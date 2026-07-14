package com.invsys.repository;

import com.invsys.domain.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByTenantIdAndProductId(UUID tenantId, UUID productId);
    Optional<ProductVariant> findByTenantIdAndBarcode(UUID tenantId, String barcode);
    Optional<ProductVariant> findByTenantIdAndSku(UUID tenantId, String sku);
}
