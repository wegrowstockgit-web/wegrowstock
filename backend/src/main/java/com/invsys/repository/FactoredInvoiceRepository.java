package com.invsys.repository;

import com.invsys.domain.FactoredInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactoredInvoiceRepository extends JpaRepository<FactoredInvoice, UUID> {
    List<FactoredInvoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<FactoredInvoice> findByTenantIdAndInvoiceId(UUID tenantId, UUID invoiceId);

    List<FactoredInvoice> findByTenantIdAndFundingStatus(UUID tenantId, String fundingStatus);
}
