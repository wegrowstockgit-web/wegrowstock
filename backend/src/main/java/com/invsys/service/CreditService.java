package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.CustomerCreditLine;
import com.invsys.repository.CustomerCreditLineRepository;
import com.invsys.tenancy.TenantContext;
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
