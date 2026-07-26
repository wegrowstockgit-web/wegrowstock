package com.invsys.service;

import com.invsys.AbstractIntegrationTest;
import com.invsys.TestDataHelper;
import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.User;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaborClockServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired LaborClockService laborClockService;
    @Autowired UserRepository userRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void clockInSwitchActivityAndRecordUnits() throws InterruptedException {
        UUID tenantId = testDataHelper.createTenant("Labor", "labor-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        User user = saveUser(tenantId);
        TenantContext.setUserId(user.getId());

        Location warehouse = new Location();
        warehouse.setTenantId(tenantId);
        warehouse.setType("WAREHOUSE");
        warehouse.setCode("WH-LAB");
        warehouse.setName("Labor WH");
        warehouse.setPath("/WH-LAB");
        warehouse = locationRepository.save(warehouse);

        LaborClockService.LaborStatus clockedIn = laborClockService.clockIn(warehouse.getId());
        assertThat(clockedIn.active()).isTrue();
        assertThat(clockedIn.currentActivity()).isEqualTo("PICKING");

        Thread.sleep(1100);
        laborClockService.recordActivityUnit(user.getId(), 3);
        laborClockService.switchActivity("INDIRECT_CLEANING");
        Thread.sleep(1100);

        LaborClockService.LaborStatus afterSwitch = laborClockService.currentStatus();
        assertThat(afterSwitch.currentActivity()).isEqualTo("INDIRECT_CLEANING");

        laborClockService.switchActivity("PICKING");
        laborClockService.recordActivityUnit(user.getId(), 2);

        LaborClockService.AnalyticsSummary analytics = laborClockService.analyticsSummary();
        assertThat(analytics.directHours()).isGreaterThan(BigDecimal.ZERO);
        assertThat(analytics.indirectHours()).isGreaterThan(BigDecimal.ZERO);
        assertThat(analytics.directPercent().add(analytics.indirectPercent()))
                .isEqualByComparingTo(new BigDecimal("100.00"));

        LaborClockService.LaborStatus clockedOut = laborClockService.clockOut();
        assertThat(clockedOut.active()).isFalse();
        assertThat(clockedOut.clockOut()).isNotNull();
    }

    @Test
    void rejectsDoubleClockIn() {
        UUID tenantId = testDataHelper.createTenant("Labor2", "labor2-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        User user = saveUser(tenantId);
        TenantContext.setUserId(user.getId());

        laborClockService.clockIn(null);

        assertThatThrownBy(() -> laborClockService.clockIn(null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private User saveUser(UUID tenantId) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail("labor@" + UUID.randomUUID() + ".test");
        user.setDisplayName("Labor User");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }
}
