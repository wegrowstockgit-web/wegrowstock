package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.CustomerCreditLine;
import com.invsys.modules.sales.repository.CustomerCreditLineRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CreditService {

    private final CustomerCreditLineRepository creditLineRepository;

    public CreditService(CustomerCreditLineRepository creditLineRepository) {
        this.creditLineRepository = creditLineRepository;
    }

    @Transactional(readOnly = true)
    public CustomerCreditLine getOrDefault(UUID customerId) {
        UUID tenantId = TenantContext.requireTenantId();
        return creditLineRepository.findByTenantIdAndCustomerId(tenantId, customerId)
                .orElseGet(() -> {
                    CustomerCreditLine line = new CustomerCreditLine();
                    line.setTenantId(tenantId);
                    line.setCustomerId(customerId);
                    line.setCreditLimit(BigDecimal.ZERO);
                    line.setAvailableCredit(BigDecimal.ZERO);
                    return line;
                });
    }

    @Transactional(readOnly = true)
    public boolean isOnHold(UUID customerId) {
        CustomerCreditLine line = getOrDefault(customerId);
        String status = line.getStatus() == null ? "ACTIVE" : line.getStatus().trim();
        return "SUSPENDED".equalsIgnoreCase(status)
                || "HOLD".equalsIgnoreCase(status)
                || "CREDIT_HOLD".equalsIgnoreCase(status);
    }

    @Transactional
    public void reserveCredit(UUID customerId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        CustomerCreditLine line = getOrCreate(customerId);
        if (line.getAvailableCredit().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CREDIT_EXCEEDED",
                    "Order total exceeds available credit");
        }
        line.setAvailableCredit(line.getAvailableCredit().subtract(amount));
        creditLineRepository.save(line);
    }

    @Transactional
    public void replenishCredit(UUID customerId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        CustomerCreditLine line = getOrCreate(customerId);
        BigDecimal next = line.getAvailableCredit().add(amount);
        if (next.compareTo(line.getCreditLimit()) > 0) {
            next = line.getCreditLimit();
        }
        line.setAvailableCredit(next);
        creditLineRepository.save(line);
    }

    @Transactional
    public CustomerCreditLine syncLimit(UUID customerId, BigDecimal limit) {
        if (limit == null) {
            return getOrDefault(customerId);
        }
        CustomerCreditLine line = getOrCreate(customerId);
        BigDecimal previousLimit = line.getCreditLimit() != null ? line.getCreditLimit() : BigDecimal.ZERO;
        line.setCreditLimit(limit);
        if (line.getAvailableCredit() == null
                || line.getAvailableCredit().signum() == 0
                || line.getAvailableCredit().compareTo(previousLimit) == 0) {
            line.setAvailableCredit(limit);
        }
        return creditLineRepository.save(line);
    }

    @Transactional
    public CustomerCreditLine placeOnHold(UUID customerId, BigDecimal fallbackLimit) {
        CustomerCreditLine line = getOrCreate(customerId);
        line.setStatus("SUSPENDED");
        if (fallbackLimit != null && (line.getCreditLimit() == null || line.getCreditLimit().signum() == 0)) {
            line.setCreditLimit(fallbackLimit);
            if (line.getAvailableCredit() == null || line.getAvailableCredit().signum() == 0) {
                line.setAvailableCredit(fallbackLimit);
            }
        }
        return creditLineRepository.save(line);
    }

    private CustomerCreditLine getOrCreate(UUID customerId) {
        UUID tenantId = TenantContext.requireTenantId();
        return creditLineRepository.findByTenantIdAndCustomerId(tenantId, customerId)
                .orElseGet(() -> {
                    CustomerCreditLine created = new CustomerCreditLine();
                    created.setTenantId(tenantId);
                    created.setCustomerId(customerId);
                    created.setCreditLimit(BigDecimal.valueOf(10000));
                    created.setAvailableCredit(BigDecimal.valueOf(10000));
                    created.setStatus("ACTIVE");
                    return creditLineRepository.save(created);
                });
    }
}
