package com.invsys;

import com.invsys.api.DashboardController;
import com.invsys.api.dto.DashboardStatsResponse;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardContractTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired DashboardController dashboardController;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;

    @BeforeEach
    void auth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "owner@test",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboardStatsExposeFrontendContractFields() {
        UUID tenantId = testDataHelper.createTenant("Dash Co", "dash-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        DashboardStatsResponse stats = dashboardController.stats();

        assertThat(stats.stockValue()).isNotNull();
        assertThat(stats.currency()).isNotBlank();
        assertThat(stats.lowStockCount()).isGreaterThanOrEqualTo(0);
        assertThat(stats.openOrdersCount()).isGreaterThanOrEqualTo(0);
        assertThat(stats.unpaidInvoicesCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void workQueueExposesActionableCountsForFrontend() {
        UUID tenantId = testDataHelper.createTenant("Queue Co", "queue-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Queue Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-QUEUE-1");
        order.setStatus("CONFIRMED");
        salesOrderRepository.save(order);

        DashboardController.WorkQueueResponse queue = dashboardController.workQueue();

        assertThat(queue.needsAllocation()).isGreaterThanOrEqualTo(1);
        assertThat(queue.readyToInvoice()).isGreaterThanOrEqualTo(0);
        assertThat(queue.unpaidInvoices()).isGreaterThanOrEqualTo(0);
        assertThat(queue.lowStockItems()).isGreaterThanOrEqualTo(0);
    }
}
