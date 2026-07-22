package com.invsys;

import com.invsys.modules.fintech.api.FintechController;
import com.invsys.modules.fintech.domain.CapitalCreditLine;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.Invoice;
import com.invsys.modules.fintech.repository.CapitalCreditLineRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.InvoiceRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FintechRlsTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired FintechController fintechController;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired CapitalCreditLineRepository creditLineRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void auth() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "owner@test",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void financingDashboardIsolatedPerTenant() {
        UUID tenantA = testDataHelper.createTenant("Fin A", "fina-" + UUID.randomUUID().toString().substring(0, 8));
        UUID tenantB = testDataHelper.createTenant("Fin B", "finb-" + UUID.randomUUID().toString().substring(0, 8));

        TenantContext.setTenantId(tenantA);
        Customer customerA = new Customer();
        customerA.setTenantId(tenantA);
        customerA.setName("Customer A");
        customerA = customerRepository.save(customerA);

        Invoice invoiceA = new Invoice();
        invoiceA.setTenantId(tenantA);
        invoiceA.setCustomerId(customerA.getId());
        invoiceA.setNumber("INV-A-RLS");
        invoiceA.setStatus("OPEN");
        invoiceA.setSubtotal(new BigDecimal("5000"));
        invoiceA.setTax(BigDecimal.ZERO);
        invoiceA.setTotal(new BigDecimal("5000"));
        invoiceA.setDueAt(Instant.now().plusSeconds(86400 * 30));
        invoiceRepository.save(invoiceA);

        FintechController.FintechDashboardResponse dashA = fintechController.dashboard();
        assertThat(dashA.eligibleInvoices()).hasSize(1);
        assertThat(dashA.eligibleInvoices().getFirst().number()).isEqualTo("INV-A-RLS");
        TenantContext.clear();

        TenantContext.setTenantId(tenantB);
        assertThat(invoiceRepository.findAll()).isEmpty();
        FintechController.FintechDashboardResponse dashB = fintechController.dashboard();
        assertThat(dashB.eligibleInvoices()).isEmpty();
        assertThat(dashB.underwriting().gmv90d()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(creditLineRepository.findByTenantId(tenantB)).isPresent();
        TenantContext.clear();

        TenantContext.setTenantId(tenantA);
        assertThat(invoiceRepository.findAll()).hasSize(1);
        assertThat(creditLineRepository.findByTenantId(tenantA)).isPresent();
    }
}
