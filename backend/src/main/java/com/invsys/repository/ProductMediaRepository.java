package com.invsys.repository;

import com.invsys.domain.ProductMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, UUID> {

    List<ProductMedia> findByTenantIdAndVariantIdOrderBySortOrderAscCreatedAtAsc(UUID tenantId, UUID variantId);

    Optional<ProductMedia> findFirstByTenantIdAndVariantIdAndPrimaryTrue(UUID tenantId, UUID variantId);

    List<ProductMedia> findByTenantIdAndVariantIdInAndPrimaryTrue(UUID tenantId, Collection<UUID> variantIds);

    @Modifying
    @Query("UPDATE ProductMedia m SET m.primary = false WHERE m.tenantId = :tenantId AND m.variantId = :variantId AND m.primary = true")
    void clearPrimary(@Param("tenantId") UUID tenantId, @Param("variantId") UUID variantId);
}
