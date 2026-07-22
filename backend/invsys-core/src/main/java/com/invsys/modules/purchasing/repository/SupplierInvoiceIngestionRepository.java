package com.invsys.modules.purchasing.repository;

import com.invsys.domain.SupplierInvoiceIngestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierInvoiceIngestionRepository extends JpaRepository<SupplierInvoiceIngestion, UUID> {
    List<SupplierInvoiceIngestion> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<SupplierInvoiceIngestion> findByTenantIdAndPurchaseOrderIdOrderByCreatedAtDesc(UUID tenantId, UUID purchaseOrderId);
}
