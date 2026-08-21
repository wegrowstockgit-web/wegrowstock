package com.invsys.modules.catalog.repository;

import com.invsys.modules.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(UUID tenantId);

    Optional<Product> findByTenantIdAndSkuRoot(UUID tenantId, String skuRoot);

    @Query("""
            SELECT p FROM Product p
            WHERE p.tenantId = :tenantId
              AND p.deletedAt IS NULL
              AND (:keyword = ''
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.skuRoot) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Product> search(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
