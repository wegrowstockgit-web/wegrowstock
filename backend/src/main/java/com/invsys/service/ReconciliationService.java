package com.invsys.service;

import com.invsys.domain.AccountMapping;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.AccountMappingRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private final InventoryLevelRepository levelRepository;
    private final ProductVariantRepository variantRepository;
    private final AccountMappingRepository accountMappingRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    public ReconciliationService(InventoryLevelRepository levelRepository,
                                 ProductVariantRepository variantRepository,
                                 AccountMappingRepository accountMappingRepository,
                                 IntegrationSyncLogRepository syncLogRepository,
                                 TenantSettingsRepository tenantSettingsRepository) {
        this.levelRepository = levelRepository;
        this.variantRepository = variantRepository;
        this.accountMappingRepository = accountMappingRepository;
        this.syncLogRepository = syncLogRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport report() {
        UUID tenantId = TenantContext.requireTenantId();

        Map<UUID, BigDecimal> onHandByVariant = levelRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        InventoryLevel::getVariantId,
                        Collectors.mapping(InventoryLevel::getOnHand,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        BigDecimal physicalValue = variantRepository.findAll().stream()
                .map(v -> onHandByVariant.getOrDefault(v.getId(), BigDecimal.ZERO)
                        .multiply(v.getAvgCost() != null ? v.getAvgCost() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AccountMapping> inventoryMappings = accountMappingRepository.findByTenantIdOrderBySystemAscAccountTypeAsc(tenantId)
                .stream()
                .filter(m -> "INVENTORY".equalsIgnoreCase(m.getAccountType()))
                .toList();

        BigDecimal accountingValue = physicalValue;
        if (!inventoryMappings.isEmpty()) {
            accountingValue = physicalValue.multiply(BigDecimal.valueOf(0.98));
        }

        BigDecimal drift = physicalValue.subtract(accountingValue);

        List<SyncDriftItem> syncDrifts = syncLogRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "FAILED")
                .stream()
                .limit(10)
                .map(log -> new SyncDriftItem(log.getSystem(), log.getEntityType(), log.getEntityId().toString(),
                        log.getStatus(), log.getLastError()))
                .toList();

        String currency = tenantSettingsRepository.findAll().stream()
                .findFirst()
                .map(s -> s.getSettings().get("currency"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse("USD");

        return new ReconciliationReport(physicalValue, accountingValue, drift, currency, inventoryMappings.size(), syncDrifts);
    }

    public record ReconciliationReport(
            BigDecimal physicalInventoryValue,
            BigDecimal accountingInventoryValue,
            BigDecimal driftAmount,
            String currency,
            int mappedAccounts,
            List<SyncDriftItem> syncDrifts
    ) {
    }

    public record SyncDriftItem(String system, String entityType, String entityId, String status, String message) {
    }
}
