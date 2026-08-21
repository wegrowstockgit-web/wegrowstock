package com.invsys.repository;

import com.invsys.domain.ProductionOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, UUID> {
    List<ProductionOrder> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("""
            SELECT po FROM ProductionOrder po
            WHERE po.tenantId = :tenantId
              AND (:keyword = ''
                OR LOWER(po.number) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(po.status) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR EXISTS (
                    SELECT 1 FROM ProductVariant v
                    WHERE v.id = po.parentVariantId AND v.tenantId = po.tenantId
                      AND LOWER(v.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                ))
            """)
    Page<ProductionOrder> search(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
