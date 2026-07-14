package com.invsys.integration.outbox;

import com.invsys.domain.IntegrationSyncLog;
import com.invsys.integration.OutboxEventHandler;
import com.invsys.integration.accounting.AccountingSyncAdapter;
import com.invsys.repository.AccountMappingRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AccountingSyncHandler implements OutboxEventHandler {

    private final Map<String, AccountingSyncAdapter> adapters;
    private final AccountMappingRepository accountMappingRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final InvoiceRepository invoiceRepository;

    public AccountingSyncHandler(List<AccountingSyncAdapter> adapterList,
                                 AccountMappingRepository accountMappingRepository,
                                 IntegrationSyncLogRepository syncLogRepository,
                                 InvoiceRepository invoiceRepository) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(AccountingSyncAdapter::system, Function.identity()));
        this.accountMappingRepository = accountMappingRepository;
        this.syncLogRepository = syncLogRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public String eventType() {
        return "LEDGER_ENTRY_ARRIVED";
    }

    @Override
    public List<String> eventTypes() {
        return List.of("INVOICE_OPEN", "INVOICE_PAID", "LEDGER_ENTRY_ARRIVED");
    }

    @Override
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        switch (eventType) {
            case "INVOICE_OPEN" -> syncInvoice(tenantId, aggregateId, false);
            case "INVOICE_PAID" -> syncInvoice(tenantId, aggregateId, true);
            case "LEDGER_ENTRY_ARRIVED" -> syncLedger(tenantId, aggregateId);
            default -> {
            }
        }
    }

    private void syncInvoice(UUID tenantId, UUID invoiceId, boolean payment) {
        if (!invoiceRepository.existsById(invoiceId)) {
            return;
        }
        for (String system : List.of("QUICKBOOKS", "XERO")) {
            if (accountMappingRepository.findByTenantIdAndSystem(tenantId, system).isEmpty()) {
                writeSkipped(tenantId, system, "INVOICE", invoiceId);
                continue;
            }
            AccountingSyncAdapter adapter = adapters.get(system);
            if (adapter != null) {
                if (payment) {
                    adapter.syncPayment(tenantId, invoiceId);
                } else {
                    adapter.syncInvoice(tenantId, invoiceId);
                }
            }
        }
    }

    private void syncLedger(UUID tenantId, UUID ledgerEntryId) {
        for (String system : List.of("QUICKBOOKS", "XERO")) {
            if (accountMappingRepository.findByTenantIdAndSystem(tenantId, system).isEmpty()) {
                writeSkipped(tenantId, system, "LEDGER_ENTRY", ledgerEntryId);
                continue;
            }
            AccountingSyncAdapter adapter = adapters.get(system);
            if (adapter != null) {
                adapter.syncLedgerEntry(tenantId, ledgerEntryId);
            }
        }
    }

    private void writeSkipped(UUID tenantId, String system, String entityType, UUID entityId) {
        IntegrationSyncLog log = new IntegrationSyncLog();
        log.setTenantId(tenantId);
        log.setSystem(system);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setStatus("SKIPPED");
        syncLogRepository.save(log);
    }
}
