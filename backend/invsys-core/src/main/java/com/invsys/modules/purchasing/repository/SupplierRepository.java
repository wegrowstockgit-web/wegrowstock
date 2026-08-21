package com.invsys.modules.purchasing.repository;

import com.invsys.modules.purchasing.domain.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findByTenantIdOrderByNameAsc(UUID tenantId);

    @Query("""
            SELECT s FROM Supplier s
            WHERE s.tenantId = :tenantId
              AND (:keyword = ''
                OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.paymentTerms, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(s.taxId, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Supplier> search(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
