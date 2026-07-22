package com.invsys.integration.outbox;

import com.invsys.modules.purchasing.service.ApOcrIngestionService;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SupplierDocumentOutboxHandler implements com.invsys.core.integration.OutboxEventHandler {

    private final ApOcrIngestionService apOcrIngestionService;

    public SupplierDocumentOutboxHandler(ApOcrIngestionService apOcrIngestionService) {
        this.apOcrIngestionService = apOcrIngestionService;
    }

    @Override
    public String eventType() {
        return "SUPPLIER_DOCUMENT_UPLOADED";
    }

    @Override
    public List<String> eventTypes() {
        return List.of("SUPPLIER_DOCUMENT_UPLOADED");
    }

    @Override
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        TenantContext.setTenantId(tenantId);
        try {
            apOcrIngestionService.reconcile(aggregateId);
        } finally {
            TenantContext.clear();
        }
    }
}
