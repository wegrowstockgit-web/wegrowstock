package com.invsys.service;

import com.invsys.repository.TenantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import com.invsys.modules.fintech.service.FintechUnderwritingService;

/**
 * Continuously refreshes capital credit lines from live GMV30d / DSO underwriting signals.
 */
@Service
public class CapitalUnderwritingScheduler {

    private static final Logger log = LoggerFactory.getLogger(CapitalUnderwritingScheduler.class);

    private final TenantRepository tenantRepository;
    private final FintechUnderwritingService fintechUnderwritingService;
    private final TransactionTemplate transactionTemplate;
    private final Executor virtualThreadExecutor;

    public CapitalUnderwritingScheduler(TenantRepository tenantRepository,
                                        FintechUnderwritingService fintechUnderwritingService,
                                        TransactionTemplate transactionTemplate,
                                        @Qualifier("virtualThreadExecutor") Executor virtualThreadExecutor) {
        this.tenantRepository = tenantRepository;
        this.fintechUnderwritingService = fintechUnderwritingService;
        this.transactionTemplate = transactionTemplate;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Scheduled(fixedDelayString = "${invsys.fintech.underwriting-refresh-interval-ms:300000}")
    public void refreshCreditLines() {
        List<UUID> tenantIds = tenantRepository.findAll().stream().map(t -> t.getId()).toList();
        for (UUID tenantId : tenantIds) {
            virtualThreadExecutor.execute(() -> refreshTenant(tenantId));
        }
    }

    void refreshTenant(UUID tenantId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                TenantContext.setTenantId(tenantId);
                try {
                    fintechUnderwritingService.refreshUnderwriting(tenantId);
                } finally {
                    TenantContext.clear();
                }
            });
        } catch (Exception e) {
            log.warn("Underwriting refresh failed for tenant={}", tenantId, e);
            TenantContext.clear();
        }
    }
}
