package com.invsys.modules.inventory.repository;

import com.invsys.modules.inventory.domain.InventoryLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {
    List<InventoryLevel> findByTenantIdAndVariantId(UUID tenantId, UUID variantId);

    List<InventoryLevel> findByTenantIdAndLocationId(UUID tenantId, UUID locationId);

    List<InventoryLevel> findByTenantIdAndLpnId(UUID tenantId, UUID lpnId);

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
            JOIN locations loc ON loc.id = il.location_id
            LEFT JOIN lots l ON il.lot_id = l.id
            WHERE il.tenant_id = :tenantId
              AND il.variant_id = :variantId
              AND il.location_id IN (:locationIds)
              AND loc.type <> 'QUARANTINE'
              AND (il.on_hand - il.allocated) > 0
            ORDER BY l.expires_at NULLS LAST, il.created_at
            FOR UPDATE OF il SKIP LOCKED
            """, nativeQuery = true)
    List<InventoryLevel> findAvailableForAllocation(
            @Param("tenantId") UUID tenantId,
            @Param("variantId") UUID variantId,
            @Param("locationIds") List<UUID> locationIds);
}
