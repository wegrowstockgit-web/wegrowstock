package com.invsys;

import com.invsys.core.common.ApiException;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.domain.CustomerCreditLine;
import com.invsys.modules.sales.repository.CustomerCreditLineRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.service.CreditService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.invsys.domain.Tenant;

class CreditServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired CustomerRepository customerRepository;
    @Autowired CustomerCreditLineRepository creditLineRepository;
    @Autowired CreditService creditService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void reserveCreditThrows422WhenExceeded() {
        UUID tenantId = testDataHelper.createTenant("Credit Tenant", "credit-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Credit Customer");
        customer = customerRepository.save(customer);
        final UUID customerId = customer.getId();

        CustomerCreditLine line = new CustomerCreditLine();
        line.setTenantId(tenantId);
        line.setCustomerId(customerId);
        line.setCreditLimit(BigDecimal.valueOf(1000));
        line.setAvailableCredit(BigDecimal.valueOf(100));
        line.setStatus("ACTIVE");
        creditLineRepository.save(line);

        assertThatThrownBy(() -> creditService.reserveCredit(customerId, BigDecimal.valueOf(500)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
