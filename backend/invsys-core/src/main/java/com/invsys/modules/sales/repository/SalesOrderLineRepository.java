package com.invsys.modules.sales.repository;

import com.invsys.modules.sales.domain.SalesOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, UUID> {
    List<SalesOrderLine> findBySalesOrderId(UUID salesOrderId);

    @Query("""
            SELECT line.variantId, COALESCE(SUM(line.qtyOrdered), 0)
            FROM SalesOrderLine line
            WHERE line.tenantId = :tenantId AND line.createdAt >= :since
            GROUP BY line.variantId
            """)
    List<Object[]> sumQtyOrderedByVariantSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);
}
