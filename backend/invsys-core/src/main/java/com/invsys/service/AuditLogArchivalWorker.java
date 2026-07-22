package com.invsys.service;

import com.invsys.domain.AuditLog;
import com.invsys.metrics.WmsMetrics;
import com.invsys.repository.AuditLogRepository;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Nightly cold archival: aged {@code audit_log} rows → gzipped JSONL on S3, then purge.
 * <p>
 * Delete runs only after a successful S3 2xx upload. Per-tenant RLS context is used for
 * reads; purge uses {@code archive_purge_audit_logs} (SECURITY DEFINER).
 */
@Service
public class AuditLogArchivalWorker {

    private static final Logger log = LoggerFactory.getLogger(AuditLogArchivalWorker.class);
    static final String JOB_LOCK_NAME = "audit-log-archival";
    private static final Duration LOCK_TTL = Duration.ofHours(3);
    private static final int MAX_BATCHES_PER_TENANT = 200;

    private final BootstrapJdbc bootstrapJdbc;
    private final AuditLogRepository auditLogRepository;
    private final AuditArchiveStorageService archiveStorageService;
    private final DistributedJobLock jobLock;
    private final ObjectMapper objectMapper;
    private final WmsMetrics wmsMetrics;
    private final AuditLogArchivalWorker self;
    private final int retentionDays;
    private final int batchSize;
    private final boolean enabled;

    public AuditLogArchivalWorker(BootstrapJdbc bootstrapJdbc,
                                  AuditLogRepository auditLogRepository,
                                  AuditArchiveStorageService archiveStorageService,
                                  DistributedJobLock jobLock,
                                  ObjectMapper objectMapper,
                                  WmsMetrics wmsMetrics,
                                  @Lazy AuditLogArchivalWorker self,
                                  @Value("${invsys.audit.archive.retention-days:90}") int retentionDays,
                                  @Value("${invsys.audit.archive.batch-size:5000}") int batchSize,
                                  @Value("${invsys.audit.archive.enabled:true}") boolean enabled) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.auditLogRepository = auditLogRepository;
        this.archiveStorageService = archiveStorageService;
        this.jobLock = jobLock;
        this.objectMapper = objectMapper;
        this.wmsMetrics = wmsMetrics;
        this.self = self;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${invsys.audit.archive.cron:0 0 2 * * ?}")
    public void runNightlyArchival() {
        if (!enabled) {
            log.debug("Audit log archival disabled (invsys.audit.archive.enabled=false)");
            return;
        }
        if (!jobLock.tryLock(JOB_LOCK_NAME, LOCK_TTL)) {
            log.info("Skipping audit log archival; another instance holds the job lock");
            return;
        }
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            List<UUID> tenantIds = bootstrapJdbc.listActiveTenantIds();
            log.info("Audit log archival starting tenants={} cutoff={} batchSize={}",
                    tenantIds.size(), cutoff, batchSize);
            int totalPurged = 0;
            for (UUID tenantId : tenantIds) {
                try {
                    totalPurged += archiveTenant(tenantId, cutoff);
                } catch (Exception ex) {
                    log.error("Audit log archival failed tenant={}", tenantId, ex);
                    wmsMetrics.incrementAuditArchiveFailure(tenantId);
                }
            }
            log.info("Audit log archival finished purgedRows={}", totalPurged);
        } finally {
            jobLock.unlock(JOB_LOCK_NAME);
        }
    }

    /**
     * Archives and purges all aged batches for one tenant. Upload happens outside any
     * delete transaction; purge is invoked only after S3 returns 2xx.
     */
    public int archiveTenant(UUID tenantId, Instant cutoff) {
        TenantContext.setTenantId(tenantId);
        try {
            int purged = 0;
            for (int batch = 0; batch < MAX_BATCHES_PER_TENANT; batch++) {
                List<AuditLog> rows = self.loadBatch(cutoff);
                if (rows.isEmpty()) {
                    break;
                }
                List<UUID> ids = rows.stream().map(AuditLog::getId).toList();
                byte[] gzipped = gzipJsonLines(rows);
                String filename = buildFilename(rows);
                // STRICT: purge only after successful upload (throws on non-2xx).
                archiveStorageService.uploadArchive(tenantId, filename, gzipped);
                purged += self.purgeAfterSuccessfulUpload(ids);
            }
            return purged;
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> loadBatch(Instant cutoff) {
        return auditLogRepository.findByCreatedAtBefore(
                cutoff,
                PageRequest.of(0, batchSize, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));
    }

    /**
     * Separate transaction: runs only after S3 upload succeeded. Rolling back here
     * never undoes an upload; rows remain for a safe retry.
     */
    @Transactional
    public int purgeAfterSuccessfulUpload(List<UUID> ids) {
        return auditLogRepository.deleteByIdIn(ids);
    }

    byte[] gzipJsonLines(List<AuditLog> rows) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                for (AuditLog row : rows) {
                    Map<String, Object> line = toArchiveLine(row);
                    byte[] json = objectMapper.writeValueAsBytes(line);
                    gzip.write(json);
                    gzip.write('\n');
                }
            }
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to gzip audit JSONL archive", ex);
        }
    }

    private Map<String, Object> toArchiveLine(AuditLog row) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("id", row.getId());
        line.put("tenantId", row.getTenantId());
        line.put("actorUserId", row.getActorUserId());
        line.put("action", row.getAction());
        line.put("entityType", row.getEntityType());
        line.put("entityId", row.getEntityId());
        line.put("diff", row.getDiff() != null ? row.getDiff() : Map.of());
        line.put("createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
        line.put("updatedAt", row.getUpdatedAt() != null ? row.getUpdatedAt().toString() : null);
        return line;
    }

    static String buildFilename(List<AuditLog> rows) {
        UUID first = rows.getFirst().getId();
        UUID last = rows.getLast().getId();
        return "audit-" + first + "-" + last + "-" + rows.size() + ".jsonl.gz";
    }
}
