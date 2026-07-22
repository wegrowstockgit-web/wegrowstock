package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.PlatformAlert;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.PlatformAlertRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformAlertService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAlertService.class);
    private static final int FAILURE_THRESHOLD = 3;
    private static final String ALERT_TYPE_SYNC_ERRORS = "INTEGRATION_SYNC_ERROR_RATE";

    private final PlatformAlertRepository platformAlertRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final TenantRepository tenantRepository;
    private final TransactionTemplate transactionTemplate;

    public PlatformAlertService(PlatformAlertRepository platformAlertRepository,
                                IntegrationSyncLogRepository syncLogRepository,
                                TenantRepository tenantRepository,
                                TransactionTemplate transactionTemplate) {
        this.platformAlertRepository = platformAlertRepository;
        this.syncLogRepository = syncLogRepository;
        this.tenantRepository = tenantRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(readOnly = true)
    public List<PlatformAlert> listOpen() {
        return platformAlertRepository.findByTenantIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(
                TenantContext.requireTenantId());
    }

    @Transactional(readOnly = true)
    public List<PlatformAlert> listAll() {
        return platformAlertRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId());
    }

    @Transactional
    public PlatformAlert acknowledge(UUID alertId) {
        PlatformAlert alert = platformAlertRepository.findById(alertId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Alert not found"));
        alert.setAcknowledgedAt(Instant.now());
        return platformAlertRepository.save(alert);
    }

    /**
     * Raise (or refresh) an open alert for the current tenant context.
     */
    @Transactional
    public PlatformAlert raise(String alertType, String severity, String sourceSystem,
                               String title, Map<String, Object> details) {
        UUID tenantId = TenantContext.requireTenantId();
        var existing = platformAlertRepository
                .findByTenantIdAndAlertTypeAndSourceSystemAndAcknowledgedAtIsNull(
                        tenantId, alertType, sourceSystem);
        if (existing.isPresent()) {
            PlatformAlert alert = existing.get();
            alert.setTitle(title);
            alert.setSeverity(severity != null ? severity : alert.getSeverity());
            Map<String, Object> merged = new LinkedHashMap<>(alert.getDetails());
            if (details != null) {
                merged.putAll(details);
            }
            alert.setDetails(merged);
            return platformAlertRepository.save(alert);
        }
        PlatformAlert alert = new PlatformAlert();
        alert.setTenantId(tenantId);
        alert.setAlertType(alertType);
        alert.setSeverity(severity != null ? severity : "WARNING");
        alert.setSourceSystem(sourceSystem);
        alert.setTitle(title);
        alert.setDetails(details != null ? new LinkedHashMap<>(details) : new LinkedHashMap<>());
        return platformAlertRepository.save(alert);
    }

    @Scheduled(fixedDelayString = "${invsys.observability.alert-scan-interval-ms:300000}")
    public void scanIntegrationHealth() {
        for (UUID tenantId : tenantRepository.findAll().stream().map(t -> t.getId()).toList()) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    TenantContext.setTenantId(tenantId);
                    try {
                        evaluateTenant(tenantId);
                    } finally {
                        TenantContext.clear();
                    }
                });
            } catch (Exception e) {
                log.warn("Integration health scan failed for tenant={}", tenantId, e);
                TenantContext.clear();
            }
        }
    }

    private void evaluateTenant(UUID tenantId) {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        List<com.invsys.domain.IntegrationSyncLog> failed =
                syncLogRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "FAILED");
        Map<String, Integer> counts = new HashMap<>();
        for (var row : failed) {
            if (row.getCreatedAt() != null && row.getCreatedAt().isBefore(since)) {
                continue;
            }
            counts.merge(row.getSystem(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < FAILURE_THRESHOLD) {
                continue;
            }
            String system = entry.getKey();
            var existing = platformAlertRepository
                    .findByTenantIdAndAlertTypeAndSourceSystemAndAcknowledgedAtIsNull(
                            tenantId, ALERT_TYPE_SYNC_ERRORS, system);
            if (existing.isPresent()) {
                PlatformAlert alert = existing.get();
                Map<String, Object> details = new LinkedHashMap<>(alert.getDetails());
                details.put("failedCount1h", entry.getValue());
                details.put("lastScannedAt", Instant.now().toString());
                alert.setDetails(details);
                alert.setTitle(system + " has " + entry.getValue() + " failed syncs in the last hour");
                platformAlertRepository.save(alert);
                continue;
            }
            PlatformAlert alert = new PlatformAlert();
            alert.setTenantId(tenantId);
            alert.setAlertType(ALERT_TYPE_SYNC_ERRORS);
            alert.setSeverity(entry.getValue() >= 10 ? "CRITICAL" : "WARNING");
            alert.setSourceSystem(system);
            alert.setTitle(system + " has " + entry.getValue() + " failed syncs in the last hour");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("failedCount1h", entry.getValue());
            details.put("threshold", FAILURE_THRESHOLD);
            details.put("windowHours", 1);
            alert.setDetails(details);
            platformAlertRepository.save(alert);
            log.warn("Raised platform alert tenant={} system={} failures={}", tenantId, system, entry.getValue());
        }
    }
}
