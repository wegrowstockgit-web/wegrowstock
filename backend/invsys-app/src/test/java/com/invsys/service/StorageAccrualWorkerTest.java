package com.invsys.service;

import com.invsys.domain.BillingAccrual;
import com.invsys.domain.BillingSla;
import com.invsys.repository.BillingAccrualRepository;
import com.invsys.repository.BillingSlaRepository;
import com.invsys.repository.TenantRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.invsys.core.tenancy.TenantContext;

@ExtendWith(MockitoExtension.class)
class StorageAccrualWorkerTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private BillingSlaRepository billingSlaRepository;
    @Mock private BillingAccrualRepository billingAccrualRepository;
    @Mock private DSLContext dsl;

    private StorageAccrualWorker worker;
    private final UUID tenantId = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private final UUID customerId = UUID.fromString("a0000000-0000-4000-8000-000000001102");

    @BeforeEach
    void setUp() {
        worker = new StorageAccrualWorker(
                tenantRepository, billingSlaRepository, billingAccrualRepository, dsl, null);
        // self proxy not needed when calling accrueForTenant directly
        try {
            var field = StorageAccrualWorker.class.getDeclaredField("self");
            field.setAccessible(true);
            field.set(worker, worker);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void clear() {
        com.invsys.core.tenancy.TenantContext.clear();
    }

    @Test
    void accrueCreatesPalletPositionAccrual() {
        BillingSla sla = new BillingSla();
        sla.setTenantId(tenantId);
        sla.setCustomerId(customerId);
        sla.setStorageMode("PALLET_POSITION");
        sla.setRatePerUnit(new BigDecimal("1.25"));
        when(billingSlaRepository.findByTenantId(tenantId)).thenReturn(List.of(sla));
        when(billingAccrualRepository.findByTenantIdAndCustomerIdAndAccrualDateAndDescription(
                eq(tenantId), eq(customerId), any(), eq(StorageAccrualWorker.STORAGE_DESCRIPTION)))
                .thenReturn(Optional.empty());
        when(dsl.fetchOne(anyString(), eq(tenantId), eq(customerId)))
                .thenReturn(positionsRecord(2));
        when(billingAccrualRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = worker.accrueForTenant(tenantId, LocalDate.of(2026, 7, 16));

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<BillingAccrual> captor = ArgumentCaptor.forClass(BillingAccrual.class);
        verify(billingAccrualRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("2.5000");
        assertThat(captor.getValue().getStatus()).isEqualTo("UNBILLED");
    }

    @Test
    void accrueCreatesCubicVolumeAccrual() {
        BillingSla sla = new BillingSla();
        sla.setTenantId(tenantId);
        sla.setCustomerId(customerId);
        sla.setStorageMode("CUBIC_VOLUME");
        sla.setRatePerUnit(new BigDecimal("0.05"));
        when(billingSlaRepository.findByTenantId(tenantId)).thenReturn(List.of(sla));
        when(billingAccrualRepository.findByTenantIdAndCustomerIdAndAccrualDateAndDescription(
                eq(tenantId), eq(customerId), any(), eq(StorageAccrualWorker.STORAGE_DESCRIPTION)))
                .thenReturn(Optional.empty());
        when(dsl.fetchOne(anyString(), eq(tenantId), eq(customerId)))
                .thenReturn(cubicRecord("12.500000"));
        when(billingAccrualRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = worker.accrueForTenant(tenantId, LocalDate.of(2026, 7, 16));

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<BillingAccrual> captor = ArgumentCaptor.forClass(BillingAccrual.class);
        verify(billingAccrualRepository).save(captor.capture());
        // 12.5 × 0.05 = 0.6250
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("0.6250");
    }

    @Test
    void accrueSkipsWhenMeasuredUnitsAreZero() {
        BillingSla sla = new BillingSla();
        sla.setTenantId(tenantId);
        sla.setCustomerId(customerId);
        sla.setStorageMode("PALLET_POSITION");
        sla.setRatePerUnit(new BigDecimal("1.25"));
        when(billingSlaRepository.findByTenantId(tenantId)).thenReturn(List.of(sla));
        when(billingAccrualRepository.findByTenantIdAndCustomerIdAndAccrualDateAndDescription(
                eq(tenantId), eq(customerId), any(), eq(StorageAccrualWorker.STORAGE_DESCRIPTION)))
                .thenReturn(Optional.empty());
        when(dsl.fetchOne(anyString(), eq(tenantId), eq(customerId)))
                .thenReturn(positionsRecord(0));

        int created = worker.accrueForTenant(tenantId, LocalDate.of(2026, 7, 16));

        assertThat(created).isZero();
        verify(billingAccrualRepository, never()).save(any());
    }

    @Test
    void accrueSkipsWhenAlreadyPresent() {
        BillingSla sla = new BillingSla();
        sla.setTenantId(tenantId);
        sla.setCustomerId(customerId);
        sla.setStorageMode("PALLET_POSITION");
        sla.setRatePerUnit(new BigDecimal("1.25"));
        when(billingSlaRepository.findByTenantId(tenantId)).thenReturn(List.of(sla));
        when(billingAccrualRepository.findByTenantIdAndCustomerIdAndAccrualDateAndDescription(
                eq(tenantId), eq(customerId), any(), eq(StorageAccrualWorker.STORAGE_DESCRIPTION)))
                .thenReturn(Optional.of(new BillingAccrual()));

        int created = worker.accrueForTenant(tenantId, LocalDate.of(2026, 7, 16));

        assertThat(created).isZero();
        verify(billingAccrualRepository, never()).save(any());
        verify(dsl, never()).fetchOne(anyString(), any(), any());
    }

    private static Record positionsRecord(int positions) {
        Field<BigDecimal> field = DSL.field("positions", BigDecimal.class);
        Record record = DSL.using(SQLDialect.POSTGRES).newRecord(field);
        record.set(field, BigDecimal.valueOf(positions));
        return record;
    }

    private static Record cubicRecord(String cubic) {
        Field<BigDecimal> field = DSL.field("cubic_units", BigDecimal.class);
        Record record = DSL.using(SQLDialect.POSTGRES).newRecord(field);
        record.set(field, new BigDecimal(cubic));
        return record;
    }
}
