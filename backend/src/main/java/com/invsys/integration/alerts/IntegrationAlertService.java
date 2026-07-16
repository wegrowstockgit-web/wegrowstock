package com.invsys.integration.alerts;

import com.invsys.common.MdcSupport;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Debounced Slack/email alerts for integration failures.
 * Redis key: {@code alert_lock:{tenantId}:{system}} TTL 60 minutes.
 */
@Service
public class IntegrationAlertService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationAlertService.class);
    private static final Duration DEBOUNCE_TTL = Duration.ofMinutes(60);

    private final TenantSettingsRepository tenantSettingsRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final SlackWebhookDispatcher slackWebhookDispatcher;
    private final SmtpEmailAlertDispatcher emailAlertDispatcher;
    private final StringRedisTemplate redis;
    private final ExecutorService virtualThreadExecutor;
    private final ConcurrentHashMap<String, Instant> localLocks = new ConcurrentHashMap<>();

    public IntegrationAlertService(
            TenantSettingsRepository tenantSettingsRepository,
            IntegrationSyncLogRepository syncLogRepository,
            SlackWebhookDispatcher slackWebhookDispatcher,
            SmtpEmailAlertDispatcher emailAlertDispatcher,
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.syncLogRepository = syncLogRepository;
        this.slackWebhookDispatcher = slackWebhookDispatcher;
        this.emailAlertDispatcher = emailAlertDispatcher;
        this.redis = redisProvider.getIfAvailable();
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @EventListener
    @Transactional
    public void onIntegrationFailure(IntegrationFailureEvent event) {
        if (event == null || event.tenantId() == null) {
            return;
        }
        String system = event.system() == null ? "UNKNOWN" : event.system().trim().toUpperCase();
        UUID entityId = event.entityId() != null ? event.entityId() : UUID.randomUUID();

        boolean previousBootstrap = TenantContext.isBootstrap();
        UUID previousTenant = TenantContext.getTenantId().orElse(null);
        try {
            TenantContext.setBootstrap(true);
            TenantContext.setTenantId(event.tenantId());

            writeSyncLog(event.tenantId(), system, entityId, event.reason(), event.detail());

            boolean shouldDispatch = event.forceDispatch() || tryAcquireLock(event.tenantId(), system);
            if (!shouldDispatch) {
                log.debug("Alert debounced tenant={} system={}", event.tenantId(), system);
                return;
            }
            UUID tenantId = event.tenantId();
            String reason = event.reason();
            String detail = event.detail();
            // Channel I/O on a context-propagating virtual thread (trace_id + MDC continuity).
            virtualThreadExecutor.execute(MdcSupport.wrapWithContext(() -> {
                boolean previous = TenantContext.isBootstrap();
                try {
                    TenantContext.setBootstrap(true);
                    TenantContext.setTenantId(tenantId);
                    MdcSupport.run(tenantId, MdcSupport.backgroundRequestId("alert", tenantId), null, () -> {
                        dispatchChannels(tenantId, system, reason, detail);
                        return null;
                    });
                } finally {
                    TenantContext.clear();
                    TenantContext.setBootstrap(previous);
                }
            }));
        } finally {
            TenantContext.clear();
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            }
            TenantContext.setBootstrap(previousBootstrap);
        }
    }
    private void writeSyncLog(UUID tenantId, String system, UUID entityId, String reason, String detail) {
        IntegrationSyncLog syncLog = new IntegrationSyncLog();
        syncLog.setTenantId(tenantId);
        syncLog.setSystem(system);
        syncLog.setEntityType("ALERT");
        syncLog.setEntityId(entityId);
        syncLog.setStatus("FAILED");
        String error = reason == null ? "INTEGRATION_FAILURE" : reason;
        if (detail != null && !detail.isBlank()) {
            error = error + ": " + detail;
        }
        if (error.length() > 500) {
            error = error.substring(0, 500);
        }
        syncLog.setLastError(error);
        syncLogRepository.save(syncLog);
    }

    private void dispatchChannels(UUID tenantId, String system, String reason, String detail) {
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId).orElse(null);
        if (settings == null) {
            log.warn("No tenant_settings for tenant={} — cannot dispatch alert", tenantId);
            return;
        }
        String title = "Integration failure: " + system;
        String body = """
                Tenant: %s
                System: %s
                Reason: %s
                Detail: %s
                Time: %s
                """.formatted(
                tenantId,
                system,
                reason != null ? reason : "UNKNOWN",
                detail != null ? detail : "",
                Instant.now());

        boolean slackOk = slackWebhookDispatcher.dispatch(settings.getSlackWebhookUrl(), title, body);
        boolean emailOk = emailAlertDispatcher.dispatch(settings.getAlertEmail(), title, body);
        log.info("Integration alert dispatched tenant={} system={} slack={} email={}",
                tenantId, system, slackOk, emailOk);
    }

    /**
     * @return true if this caller acquired the debounce lock (may send alert)
     */
    boolean tryAcquireLock(UUID tenantId, String system) {
        String key = lockKey(tenantId, system);
        if (redis != null) {
            try {
                Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", DEBOUNCE_TTL);
                return Boolean.TRUE.equals(acquired);
            } catch (RuntimeException ex) {
                log.warn("Redis alert lock failed, using local fallback: {}", ex.getMessage());
            }
        }
        Instant now = Instant.now();
        Instant[] outcome = new Instant[1];
        localLocks.compute(key, (k, existing) -> {
            if (existing != null && existing.isAfter(now)) {
                outcome[0] = existing;
                return existing;
            }
            Instant until = now.plus(DEBOUNCE_TTL);
            outcome[0] = null;
            return until;
        });
        return outcome[0] == null;
    }

    void clearLock(UUID tenantId, String system) {
        String key = lockKey(tenantId, system);
        localLocks.remove(key);
        if (redis != null) {
            try {
                redis.delete(key);
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
    }

    private static String lockKey(UUID tenantId, String system) {
        return "alert_lock:" + tenantId + ":" + system.toUpperCase();
    }

    public record AlertPreferencesDto(
            String alertEmail,
            String slackWebhookUrl,
            boolean slackConfigured,
            boolean emailConfigured
    ) {
    }

    public record UpdateAlertPreferencesRequest(String alertEmail, String slackWebhookUrl) {
    }

    public AlertPreferencesDto getPreferences() {
        UUID tenantId = TenantContext.requireTenantId();
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> TenantSettings.withDefaults(tenantId));
        return toDto(settings);
    }

    @Transactional
    public AlertPreferencesDto updatePreferences(UpdateAlertPreferencesRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        TenantSettings settings = tenantSettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> TenantSettings.withDefaults(tenantId));
        if (request.alertEmail() != null) {
            String email = request.alertEmail().isBlank() ? null : request.alertEmail().trim();
            settings.setAlertEmail(email);
        }
        if (request.slackWebhookUrl() != null) {
            String url = request.slackWebhookUrl().trim();
            // Ignore masked GET payloads so a re-save does not clobber the real webhook.
            if (url.contains("…") || url.contains("...")) {
                // keep existing
            } else if (url.isBlank()) {
                settings.setSlackWebhookUrl(null);
            } else {
                settings.setSlackWebhookUrl(url);
            }
        }
        tenantSettingsRepository.save(settings);
        return toDto(settings);
    }

    public Map<String, Object> sendTestAlert() {
        UUID tenantId = TenantContext.requireTenantId();
        clearLock(tenantId, "TEST");
        onIntegrationFailure(new IntegrationFailureEvent(
                tenantId,
                "TEST",
                "TEST_ALERT",
                "Manual test alert from Settings → System Alerts",
                null,
                true));
        return Map.of(
                "status", "sent",
                "tenantId", tenantId.toString(),
                "system", "TEST"
        );
    }

    private static AlertPreferencesDto toDto(TenantSettings settings) {
        String email = settings.getAlertEmail();
        String webhook = settings.getSlackWebhookUrl();
        return new AlertPreferencesDto(
                email,
                maskWebhook(webhook),
                webhook != null && !webhook.isBlank(),
                email != null && !email.isBlank()
        );
    }

    static String maskWebhook(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.length() <= 12) {
            return "********";
        }
        return trimmed.substring(0, 8) + "…" + trimmed.substring(trimmed.length() - 4);
    }
}
