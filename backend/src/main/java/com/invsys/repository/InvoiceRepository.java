package com.invsys.repository;

import com.invsys.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<Invoice> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);
    long countByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);
    Optional<Invoice> findByTenantIdAndNumber(UUID tenantId, String number);
    Optional<Invoice> findByTenantIdAndSalesOrderId(UUID tenantId, UUID salesOrderId);
}
