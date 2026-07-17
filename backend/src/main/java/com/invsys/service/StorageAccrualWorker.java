package com.invsys.service;

import com.invsys.domain.BillingAccrual;
import com.invsys.domain.BillingSla;
import com.invsys.repository.BillingAccrualRepository;
import com.invsys.repository.BillingSlaRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.tenancy.TenantContext;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Midnight 3PL storage accrual engine. Fans out per-tenant work on virtual threads.
 */
@Service
public class StorageAccrualWorker {

    private static final Logger log = LoggerFactory.getLogger(StorageAccrualWorker.class);
    public static final String STORAGE_DESCRIPTION = "Daily storage accrual";

    private final TenantRepository tenantRepository;
    private final BillingSlaRepository billingSlaRepository;
    private final BillingAccrualRepository billingAccrualRepository;
    private final DSLContext dsl;
    private final StorageAccrualWorker self;

    public StorageAccrualWorker(TenantRepository tenantRepository,
                                BillingSlaRepository billingSlaRepository,
                                BillingAccrualRepository billingAccrualRepository,
                                DSLContext dsl,
                                @Lazy StorageAccrualWorker self) {
        this.tenantRepository = tenantRepository;
        this.billingSlaRepository = billingSlaRepository;
        this.billingAccrualRepository = billingAccrualRepository;
        this.dsl = dsl;
        this.self = self;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void runMidnightAccruals() {
        LocalDate accrualDate = LocalDate.now(ZoneOffset.UTC);
        List<UUID> tenantIds = tenantRepository.findAll().stream().map(t -> t.getId()).toList();
        if (tenantIds.isEmpty()) {
            return;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (UUID tenantId : tenantIds) {
                futures.add(executor.submit(() -> {
                    try {
                        int created = self.accrueForTenant(tenantId, accrualDate);
                        if (created > 0) {
                            log.info("3PL storage accruals tenant={} date={} created={}",
                                    tenantId, accrualDate, created);
                        }
                    } catch (Exception ex) {
                        log.error("3PL storage accrual failed tenant={} date={}", tenantId, accrualDate, ex);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (Exception ex) {
            log.error("3PL storage accrual fan-out failed date={}", accrualDate, ex);
        }
    }

    @Transactional
    public int accrueForTenant(UUID tenantId, LocalDate accrualDate) {
        TenantContext.setTenantId(tenantId);
        try {
            List<BillingSla> slas = billingSlaRepository.findByTenantId(tenantId);
            int created = 0;
            for (BillingSla sla : slas) {
                if (billingAccrualRepository
                        .findByTenantIdAndCustomerIdAndAccrualDateAndDescription(
                                tenantId, sla.getCustomerId(), accrualDate, STORAGE_DESCRIPTION)
                        .isPresent()) {
                    continue;
                }
                BigDecimal units = measureBillableUnits(tenantId, sla);
                if (units.signum() <= 0) {
                    continue;
                }
                BigDecimal rate = sla.getRatePerUnit() != null ? sla.getRatePerUnit() : BigDecimal.ZERO;
                BigDecimal amount = units.multiply(rate).setScale(4, RoundingMode.HALF_UP);
                if (amount.signum() <= 0) {
                    continue;
                }

                BillingAccrual accrual = new BillingAccrual();
                accrual.setTenantId(tenantId);
                accrual.setCustomerId(sla.getCustomerId());
                accrual.setAccrualDate(accrualDate);
                accrual.setAmount(amount);
                accrual.setDescription(STORAGE_DESCRIPTION);
                accrual.setStatus("UNBILLED");
                billingAccrualRepository.save(accrual);
                created++;
            }
            return created;
        } finally {
            TenantContext.clear();
        }
    }

    BigDecimal measureBillableUnits(UUID tenantId, BillingSla sla) {
        String mode = sla.getStorageMode() == null ? "PALLET_POSITION" : sla.getStorageMode();
        if ("CUBIC_VOLUME".equalsIgnoreCase(mode)) {
            return measureCubicVolume(tenantId, sla.getCustomerId());
        }
        return measurePalletPositions(tenantId, sla.getCustomerId());
    }

    private BigDecimal measurePalletPositions(UUID tenantId, UUID customerId) {
        Record row = dsl.fetchOne("""
                SELECT COUNT(DISTINCT il.location_id) AS positions
                FROM inventory_levels il
                JOIN locations loc ON loc.id = il.location_id
                WHERE il.tenant_id = ?
                  AND il.owner_customer_id = ?
                  AND il.on_hand > 0
                  AND loc.type IN ('PALLET', 'BIN')
                """, tenantId, customerId);
        if (row == null || row.get("positions") == null) {
            return BigDecimal.ZERO;
        }
        return row.get("positions", BigDecimal.class);
    }

    private BigDecimal measureCubicVolume(UUID tenantId, UUID customerId) {
        Record row = dsl.fetchOne("""
                SELECT COALESCE(SUM(
                    il.on_hand * COALESCE(
                        NULLIF(pv.volume, 0),
                        CASE
                            WHEN pv.length IS NOT NULL AND pv.width IS NOT NULL AND pv.height IS NOT NULL
                             AND pv.length > 0 AND pv.width > 0 AND pv.height > 0
                            THEN (pv.length * pv.width * pv.height) / 1728.0
                            ELSE 0
                        END
                    )
                ), 0) AS cubic_units
                FROM inventory_levels il
                JOIN product_variants pv ON pv.id = il.variant_id
                WHERE il.tenant_id = ?
                  AND il.owner_customer_id = ?
                  AND il.on_hand > 0
                """, tenantId, customerId);
        if (row == null || row.get("cubic_units") == null) {
            return BigDecimal.ZERO;
        }
        return row.get("cubic_units", BigDecimal.class).setScale(6, RoundingMode.HALF_UP);
    }
}
