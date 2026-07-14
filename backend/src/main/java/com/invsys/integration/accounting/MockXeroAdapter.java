package com.invsys.integration.accounting;

import com.invsys.domain.IntegrationSyncLog;
import com.invsys.repository.IntegrationSyncLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "invsys.integration.accounting.mock", havingValue = "true")
public class MockXeroAdapter implements AccountingSyncAdapter {

    private final IntegrationSyncLogRepository syncLogRepository;

    public MockXeroAdapter(IntegrationSyncLogRepository syncLogRepository) {
        this.syncLogRepository = syncLogRepository;
    }

    @Override
    public String system() {
        return "XERO";
    }

    @Override
    public IntegrationSyncLog syncInvoice(UUID tenantId, UUID invoiceId) {
        return writeLog(tenantId, "INVOICE", invoiceId);
    }

    @Override
    public IntegrationSyncLog syncPayment(UUID tenantId, UUID invoiceId) {
        return writeLog(tenantId, "PAYMENT", invoiceId);
    }

    @Override
    public IntegrationSyncLog syncLedgerEntry(UUID tenantId, UUID ledgerEntryId) {
        return writeLog(tenantId, "LEDGER_ENTRY", ledgerEntryId);
    }

    private IntegrationSyncLog writeLog(UUID tenantId, String entityType, UUID entityId) {
        IntegrationSyncLog log = new IntegrationSyncLog();
        log.setTenantId(tenantId);
        log.setSystem(system());
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setStatus("SYNCED");
        return syncLogRepository.save(log);
    }
}
