package com.invsys.service;

import com.invsys.domain.IntegrationSyncLog;
import com.invsys.domain.Invoice;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Flags OPEN invoices past due_at with WARNING rows in integration_sync_logs.
 */
@Service
public class OverdueInvoiceScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceScheduler.class);

    private final TenantRepository tenantRepository;
    private final InvoiceRepository invoiceRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final TransactionTemplate transactionTemplate;
    private final Executor virtualThreadExecutor;

    public OverdueInvoiceScheduler(TenantRepository tenantRepository,
                                   InvoiceRepository invoiceRepository,
                                   IntegrationSyncLogRepository syncLogRepository,
                                   TransactionTemplate transactionTemplate,
                                   @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
        this.tenantRepository = tenantRepository;
        this.invoiceRepository = invoiceRepository;
        this.syncLogRepository = syncLogRepository;
        this.transactionTemplate = transactionTemplate;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Scheduled(fixedDelayString = "${invsys.invoicing.overdue-check-interval-ms:900000}")
    public void flagOverdueInvoices() {
        List<UUID> tenantIds = tenantRepository.findAll().stream().map(t -> t.getId()).toList();
        for (UUID tenantId : tenantIds) {
            virtualThreadExecutor.execute(() -> processTenant(tenantId));
        }
    }

    void processTenant(UUID tenantId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                TenantContext.setTenantId(tenantId);
                try {
                    Instant now = Instant.now();
                    List<Invoice> overdue = invoiceRepository
                            .findByTenantIdAndStatusAndDueAtBefore(tenantId, "OPEN", now);
                    for (Invoice invoice : overdue) {
                        boolean alreadyLogged = syncLogRepository
                                .findByTenantIdAndSystemAndStatusOrderByCreatedAtDesc(
                                        tenantId, "INVOICING", "WARNING")
                                .stream()
                                .anyMatch(logEntry -> invoice.getId().equals(logEntry.getEntityId()));
                        if (alreadyLogged) {
                            continue;
                        }
                        IntegrationSyncLog warning = new IntegrationSyncLog();
                        warning.setTenantId(tenantId);
                        warning.setSystem("INVOICING");
                        warning.setEntityType("INVOICE");
                        warning.setEntityId(invoice.getId());
                        warning.setStatus("WARNING");
                        warning.setLastError("Invoice " + invoice.getNumber() + " is past due (" + invoice.getDueAt() + ")");
                        syncLogRepository.save(warning);
                    }
                } finally {
                    TenantContext.clear();
                }
            });
        } catch (Exception e) {
            log.warn("Overdue invoice check failed for tenant={}", tenantId, e);
            TenantContext.clear();
        }
    }
}
