package com.invsys.repository;

import com.invsys.domain.BomLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BomLineRepository extends JpaRepository<BomLine, UUID> {
    List<BomLine> findByBomId(UUID bomId);

    @Query(value = """
            WITH RECURSIVE bom_tree AS (
                SELECT bl.component_variant_id, bl.quantity_required::numeric AS quantity_required, 1 AS depth
                FROM bom_lines bl
                JOIN boms b ON b.id = bl.bom_id
                WHERE b.tenant_id = :tenantId AND b.parent_variant_id = :parentVariantId AND b.is_active = TRUE
                UNION ALL
                SELECT bl2.component_variant_id, (bt.quantity_required * bl2.quantity_required)::numeric, bt.depth + 1
                FROM bom_tree bt
                JOIN boms b2 ON b2.parent_variant_id = bt.component_variant_id AND b2.tenant_id = :tenantId AND b2.is_active = TRUE
                JOIN bom_lines bl2 ON bl2.bom_id = b2.id
                WHERE bt.depth < 20
            )
            SELECT component_variant_id, SUM(quantity_required) AS qty
            FROM bom_tree
            GROUP BY component_variant_id
            """, nativeQuery = true)
    List<Object[]> explodeBom(@Param("tenantId") UUID tenantId, @Param("parentVariantId") UUID parentVariantId);

    @Query(value = """
            WITH RECURSIVE cycle_check AS (
                SELECT bl.component_variant_id, ARRAY[:parentVariantId] AS path, 1 AS depth
                FROM bom_lines bl
                JOIN boms b ON b.id = bl.bom_id
                WHERE b.tenant_id = :tenantId AND b.parent_variant_id = :parentVariantId
                UNION ALL
                SELECT bl2.component_variant_id, cc.path || bl2.component_variant_id, cc.depth + 1
                FROM cycle_check cc
                JOIN boms b2 ON b2.parent_variant_id = cc.component_variant_id AND b2.tenant_id = :tenantId
                JOIN bom_lines bl2 ON bl2.bom_id = b2.id
                WHERE cc.depth < 20 AND NOT (bl2.component_variant_id = ANY(cc.path))
            )
            SELECT EXISTS (
                SELECT 1 FROM cycle_check WHERE component_variant_id = :parentVariantId
            )
            """, nativeQuery = true)
    boolean hasCycle(@Param("tenantId") UUID tenantId, @Param("parentVariantId") UUID parentVariantId);

    @Query(value = """
            WITH RECURSIVE cycle_check AS (
                SELECT bl.component_variant_id, ARRAY[:parentVariantId, :componentVariantId] AS path, 1 AS depth
                FROM bom_lines bl
                JOIN boms b ON b.id = bl.bom_id
                WHERE b.tenant_id = :tenantId AND b.parent_variant_id = :parentVariantId
                UNION ALL
                SELECT bl2.component_variant_id, cc.path || bl2.component_variant_id, cc.depth + 1
                FROM cycle_check cc
                JOIN boms b2 ON b2.parent_variant_id = cc.component_variant_id AND b2.tenant_id = :tenantId
                JOIN bom_lines bl2 ON bl2.bom_id = b2.id
                WHERE cc.depth < 20 AND NOT (bl2.component_variant_id = ANY(cc.path))
            )
            SELECT EXISTS (
                SELECT 1 FROM cycle_check WHERE component_variant_id = :parentVariantId
            )
            """, nativeQuery = true)
    boolean wouldCreateCycle(@Param("tenantId") UUID tenantId,
                             @Param("parentVariantId") UUID parentVariantId,
                             @Param("componentVariantId") UUID componentVariantId);
}
