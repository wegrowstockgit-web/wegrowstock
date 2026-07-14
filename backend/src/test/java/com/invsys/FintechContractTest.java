package com.invsys;

import com.invsys.api.FintechController;
import com.invsys.domain.Customer;
import com.invsys.domain.Invoice;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.tenancy.TenantContext;
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

class FintechContractTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired FintechController fintechController;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired CustomerRepository customerRepository;

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
    void fintechDashboardMatchesFrontendContract() {
        UUID tenantId = testDataHelper.createTenant("Fin Contract", "finctr-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Contract Customer");
        customer = customerRepository.save(customer);

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setCustomerId(customer.getId());
        invoice.setNumber("INV-CTR-1");
        invoice.setStatus("OPEN");
        invoice.setSubtotal(new BigDecimal("2000"));
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(new BigDecimal("2000"));
        invoice.setDueAt(Instant.now().plusSeconds(86400 * 14));
        invoiceRepository.save(invoice);

        FintechController.FintechDashboardResponse dash = fintechController.dashboard();

        assertThat(dash.creditLine()).isNotNull();
        assertThat(dash.creditLine().creditLimit()).isNotNull();
        assertThat(dash.creditLine().outstandingBalance()).isNotNull();
        assertThat(dash.creditLine().interestRateApr()).isNotNull();
        assertThat(dash.creditLine().utilizationStatus()).isNotBlank();
        assertThat(dash.utilizationPercent()).isNotNull();
        assertThat(dash.underwriting()).isNotNull();
        assertThat(dash.underwriting().gmv90d()).isNotNull();
        assertThat(dash.underwriting().avgInvoiceAgeDays()).isNotNull();
        assertThat(dash.underwriting().paymentVelocityScore()).isNotNull();
        assertThat(dash.underwriting().eligibleFactoringLimit()).isNotNull();
        assertThat(dash.eligibleInvoices()).isNotEmpty();
        assertThat(dash.eligibleInvoices().getFirst().invoiceId()).isNotNull();
        assertThat(dash.eligibleInvoices().getFirst().number()).isNotBlank();
        assertThat(dash.eligibleInvoices().getFirst().total()).isNotNull();
        assertThat(dash.eligibleInvoices().getFirst().advanceAmount()).isNotNull();
    }
}
