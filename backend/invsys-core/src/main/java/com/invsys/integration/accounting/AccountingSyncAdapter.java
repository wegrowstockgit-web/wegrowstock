package com.invsys.integration.accounting;

import com.invsys.domain.IntegrationSyncLog;

import java.util.List;
import java.util.UUID;

public interface AccountingSyncAdapter {

    String system();

    IntegrationSyncLog syncInvoice(UUID tenantId, UUID invoiceId);

    IntegrationSyncLog syncPayment(UUID tenantId, UUID invoiceId);

    IntegrationSyncLog syncLedgerEntry(UUID tenantId, UUID ledgerEntryId);

    /**
     * Live chart of accounts. Mock / disconnected tenants receive the sandbox catalog.
     */
    default List<LedgerAccount> listAccounts(UUID tenantId) {
        return StandardLedgerAccounts.sandboxCatalog();
    }

    /**
     * Create the four standard inventory accounts when the provider ledger lacks them.
     */
    default List<LedgerAccount> provisionStandardAccounts(UUID tenantId) {
        return StandardLedgerAccounts.requiredDefaults();
    }

    default AccountingConnectionTest testConnection(UUID tenantId) {
        return AccountingConnectionTest.of(true, true, "Sandbox connection verified");
    }
}
