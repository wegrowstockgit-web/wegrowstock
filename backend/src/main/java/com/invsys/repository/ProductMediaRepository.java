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

    Optional<ProductMedia> findByTenantIdAndIdAndVariantId(UUID tenantId, UUID id, UUID variantId);

    List<ProductMedia> findByTenantIdAndVariantIdInAndPrimaryTrue(UUID tenantId, Collection<UUID> variantIds);

    List<ProductMedia> findByTenantIdAndUrl(UUID tenantId, String url);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductMedia m SET m.primary = false WHERE m.tenantId = :tenantId AND m.variantId = :variantId AND m.primary = true")
    void clearPrimary(@Param("tenantId") UUID tenantId, @Param("variantId") UUID variantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProductMedia m WHERE m.tenantId = :tenantId AND m.url = :url")
    void deleteByTenantIdAndUrl(@Param("tenantId") UUID tenantId, @Param("url") String url);
}
