package com.invsys.repository;

import com.invsys.domain.InventoryLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {
    List<InventoryLevel> findByTenantIdAndVariantId(UUID tenantId, UUID variantId);

    @Query(value = """
            SELECT il.* FROM inventory_levels il
            WHERE il.tenant_id = :tenantId
              AND il.variant_id = :variantId
              AND il.location_id = :locationId
              AND ((:lotId IS NULL AND il.lot_id IS NULL) OR il.lot_id = :lotId)
            LIMIT 1
            FOR UPDATE OF il SKIP LOCKED
            """, nativeQuery = true)
    java.util.Optional<InventoryLevel> lockLevelForAllocation(
            @Param("tenantId") UUID tenantId,
            @Param("variantId") UUID variantId,
            @Param("locationId") UUID locationId,
            @Param("lotId") UUID lotId);

    @Query(value = """
            SELECT il.* FROM inventory_levels il
            LEFT JOIN lots l ON il.lot_id = l.id
            WHERE il.tenant_id = :tenantId
              AND il.variant_id = :variantId
              AND il.location_id IN (:locationIds)
              AND (il.on_hand - il.allocated) > 0
            ORDER BY l.expires_at NULLS LAST, il.created_at
            FOR UPDATE OF il SKIP LOCKED
            """, nativeQuery = true)
    List<InventoryLevel> findAvailableForAllocation(
            @Param("tenantId") UUID tenantId,
            @Param("variantId") UUID variantId,
            @Param("locationIds") List<UUID> locationIds);
}
