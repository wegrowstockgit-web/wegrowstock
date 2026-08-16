package com.invsys.modules.sales.api;

import com.invsys.modules.sales.domain.Invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceLookup {

    Optional<Invoice> findById(UUID id);

    List<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
