package com.invsys.integration.alerts;

import com.invsys.AbstractIntegrationTest;
import com.invsys.TestDataHelper;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationAlertServiceTest extends AbstractIntegrationTest {

    @Autowired IntegrationAlertService alertService;
    @Autowired TestDataHelper testDataHelper;
    @Autowired TenantSettingsRepository tenantSettingsRepository;
    @Autowired IntegrationSyncLogRepository syncLogRepository;
    @Autowired IntegrationFailurePublisher failurePublisher;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void debounceSuppressesSecondAlertButStillLogs() {
        UUID tenantId = testDataHelper.createTenant("Alert Co", "alert-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        TenantSettings settings = TenantSettings.withDefaults(tenantId);
        settings.setAlertEmail("ops@example.com");
        settings.setSlackWebhookUrl("https://hooks.slack.com/services/T00/B00/test");
        tenantSettingsRepository.save(settings);

        alertService.clearLock(tenantId, "SHOPIFY");
        long before = syncLogRepository.findByTenantIdAndSystemOrderByCreatedAtDesc(tenantId, "SHOPIFY").size();

        failurePublisher.publish(tenantId, "SHOPIFY", "HTTP_500", "boom-1");
        failurePublisher.publish(tenantId, "SHOPIFY", "HTTP_500", "boom-2");

        long after = syncLogRepository.findByTenantIdAndSystemOrderByCreatedAtDesc(tenantId, "SHOPIFY").size();
        assertThat(after - before).isGreaterThanOrEqualTo(2);
        assertThat(alertService.tryAcquireLock(tenantId, "SHOPIFY")).isFalse();
    }

    @Test
    void testAlertBypassesDebounce() {
        UUID tenantId = testDataHelper.createTenant("Alert Test", "alert-t-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        TenantSettings settings = TenantSettings.withDefaults(tenantId);
        settings.setAlertEmail("ops@example.com");
        tenantSettingsRepository.save(settings);

        alertService.clearLock(tenantId, "TEST");
        // Acquire lock as if a prior alert fired
        assertThat(alertService.tryAcquireLock(tenantId, "TEST")).isTrue();

        var result = alertService.sendTestAlert();
        assertThat(result.get("status")).isEqualTo("sent");
        assertThat(syncLogRepository.findByTenantIdAndSystemOrderByCreatedAtDesc(tenantId, "TEST"))
                .isNotEmpty();
    }

    @Test
    void masksSlackWebhookOnRead() {
        assertThat(IntegrationAlertService.maskWebhook("https://hooks.slack.com/services/SECRET"))
                .startsWith("https://")
                .contains("…")
                .doesNotContain("SECRET");
    }
}
