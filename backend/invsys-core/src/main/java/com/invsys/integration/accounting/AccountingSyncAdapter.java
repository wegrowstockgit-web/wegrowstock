package com.invsys.integration.accounting;

import com.invsys.domain.IntegrationSyncLog;

import java.util.UUID;

public interface AccountingSyncAdapter {

    String system();

    IntegrationSyncLog syncInvoice(UUID tenantId, UUID invoiceId);

    IntegrationSyncLog syncPayment(UUID tenantId, UUID invoiceId);

    IntegrationSyncLog syncLedgerEntry(UUID tenantId, UUID ledgerEntryId);
}
