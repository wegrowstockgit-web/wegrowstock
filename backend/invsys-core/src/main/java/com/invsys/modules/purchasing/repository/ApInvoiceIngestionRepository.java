package com.invsys.modules.purchasing.repository;

import com.invsys.modules.purchasing.domain.ApInvoiceIngestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApInvoiceIngestionRepository extends JpaRepository<ApInvoiceIngestion, UUID> {

    List<ApInvoiceIngestion> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
