package com.invsys.modules.sales.repository;

import com.invsys.modules.sales.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByTenantIdOrderByNameAsc(UUID tenantId);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND (:keyword = ''
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(c.customerStatus, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(c.paymentTerms, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Customer> search(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
