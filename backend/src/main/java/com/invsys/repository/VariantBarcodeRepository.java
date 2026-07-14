package com.invsys.repository;

import com.invsys.domain.VariantBarcode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VariantBarcodeRepository extends JpaRepository<VariantBarcode, UUID> {
    List<VariantBarcode> findByTenantIdAndVariantIdOrderByCreatedAtAsc(UUID tenantId, UUID variantId);

    Optional<VariantBarcode> findByTenantIdAndBarcode(UUID tenantId, String barcode);

    Optional<VariantBarcode> findByIdAndTenantIdAndVariantId(UUID id, UUID tenantId, UUID variantId);
}
