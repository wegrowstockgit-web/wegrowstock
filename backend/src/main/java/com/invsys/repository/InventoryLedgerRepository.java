package com.invsys.repository;

import com.invsys.domain.InventoryLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedger, UUID> {
    List<InventoryLedger> findByTenantIdAndVariantIdOrderByCreatedAtDesc(UUID tenantId, UUID variantId);

    @Query("""
            SELECT l.locationId, COUNT(l), COALESCE(SUM(ABS(l.quantityDelta)), 0)
            FROM InventoryLedger l
            WHERE l.tenantId = :tenantId AND l.createdAt >= :since
            GROUP BY l.locationId
            """)
    List<Object[]> movementStatsByLocationSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);

    @Query("""
            SELECT l.locationId, COUNT(l)
            FROM InventoryLedger l
            WHERE l.tenantId = :tenantId AND l.movementType = 'ADJUST' AND l.quantityDelta < 0
            AND l.createdAt >= :since
            GROUP BY l.locationId
            """)
    List<Object[]> negativeAdjustCountsByLocationSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);
}
