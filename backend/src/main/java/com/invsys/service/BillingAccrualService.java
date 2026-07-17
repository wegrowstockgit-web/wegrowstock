package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.BillingAccrual;
import com.invsys.domain.BillingSla;
import com.invsys.domain.Customer;
import com.invsys.repository.BillingAccrualRepository;
import com.invsys.repository.BillingSlaRepository;
import com.invsys.repository.CustomerRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BillingAccrualService {

    private static final Set<String> STORAGE_MODES = Set.of("PALLET_POSITION", "CUBIC_VOLUME");

    private final BillingSlaRepository billingSlaRepository;
    private final BillingAccrualRepository billingAccrualRepository;
    private final CustomerRepository customerRepository;

    public BillingAccrualService(BillingSlaRepository billingSlaRepository,
                                 BillingAccrualRepository billingAccrualRepository,
                                 CustomerRepository customerRepository) {
        this.billingSlaRepository = billingSlaRepository;
        this.billingAccrualRepository = billingAccrualRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public CustomerBillingView officeBilling(UUID customerId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireCustomer(tenantId, customerId);
        BillingSla sla = billingSlaRepository.findByTenantIdAndCustomerId(tenantId, customerId).orElse(null);
        List<BillingAccrual> unbilled = billingAccrualRepository
                .findByTenantIdAndCustomerIdAndStatusOrderByAccrualDateDesc(tenantId, customerId, "UNBILLED");
        BigDecimal unbilledTotal = unbilled.stream()
                .map(BillingAccrual::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CustomerBillingView(sla, unbilled, unbilledTotal);
    }

    @Transactional
    public BillingSla upsertSla(UUID customerId, String storageMode, BigDecimal ratePerUnit,
                                BigDecimal pickFeePerItem) {
        UUID tenantId = TenantContext.requireTenantId();
        requireCustomer(tenantId, customerId);
        String mode = normalizeMode(storageMode);
        BillingSla sla = billingSlaRepository.findByTenantIdAndCustomerId(tenantId, customerId)
                .orElseGet(() -> {
                    BillingSla created = new BillingSla();
                    created.setTenantId(tenantId);
                    created.setCustomerId(customerId);
                    return created;
                });
        sla.setStorageMode(mode);
        sla.setRatePerUnit(ratePerUnit != null ? ratePerUnit : BigDecimal.ZERO);
        sla.setPickFeePerItem(pickFeePerItem != null ? pickFeePerItem : BigDecimal.ZERO);
        return billingSlaRepository.save(sla);
    }

    @Transactional(readOnly = true)
    public ShowroomAccrualsView showroomMonthToDate() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = TenantContext.requireCustomerId();
        LocalDate monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        List<BillingAccrual> monthAccruals = billingAccrualRepository
                .findByTenantIdAndCustomerIdAndAccrualDateGreaterThanEqualAndStatusOrderByAccrualDateDesc(
                        tenantId, customerId, monthStart, "UNBILLED");
        // Also surface BILLED MTD for transparency
        List<BillingAccrual> billedMtd = billingAccrualRepository
                .findByTenantIdAndCustomerIdAndAccrualDateGreaterThanEqualAndStatusOrderByAccrualDateDesc(
                        tenantId, customerId, monthStart, "BILLED");
        BigDecimal mtdTotal = monthAccruals.stream().map(BillingAccrual::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(billedMtd.stream().map(BillingAccrual::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        BillingSla sla = billingSlaRepository.findByTenantIdAndCustomerId(tenantId, customerId).orElse(null);
        List<BillingAccrual> combined = new java.util.ArrayList<>(monthAccruals);
        combined.addAll(billedMtd);
        combined.sort((a, b) -> b.getAccrualDate().compareTo(a.getAccrualDate()));
        return new ShowroomAccrualsView(sla, combined, mtdTotal, monthStart);
    }

    private Customer requireCustomer(UUID tenantId, UUID customerId) {
        return customerRepository.findById(customerId)
                .filter(c -> tenantId.equals(c.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Customer not found"));
    }

    private static String normalizeMode(String storageMode) {
        if (storageMode == null || storageMode.isBlank()) {
            return "PALLET_POSITION";
        }
        String mode = storageMode.trim().toUpperCase(Locale.ROOT);
        if (!STORAGE_MODES.contains(mode)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "storage_mode must be PALLET_POSITION or CUBIC_VOLUME");
        }
        return mode;
    }

    public record CustomerBillingView(
            BillingSla sla,
            List<BillingAccrual> unbilledAccruals,
            BigDecimal unbilledTotal
    ) {
    }

    public record ShowroomAccrualsView(
            BillingSla sla,
            List<BillingAccrual> accruals,
            BigDecimal monthToDateTotal,
            LocalDate monthStart
    ) {
    }
}
