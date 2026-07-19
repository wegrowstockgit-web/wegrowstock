package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.AuditLog;
import com.invsys.metrics.WmsMetrics;
import com.invsys.repository.AuditLogRepository;
import com.invsys.tenancy.BootstrapJdbc;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogArchivalWorkerTest {

    @Mock BootstrapJdbc bootstrapJdbc;
    @Mock AuditLogRepository auditLogRepository;
    @Mock AuditArchiveStorageService archiveStorageService;
    @Mock DistributedJobLock jobLock;
    @Mock WmsMetrics wmsMetrics;

    private AuditLogArchivalWorker worker;
    private final UUID tenantId = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        worker = new AuditLogArchivalWorker(
                bootstrapJdbc,
                auditLogRepository,
                archiveStorageService,
                jobLock,
                objectMapper,
                wmsMetrics,
                null,
                90,
                5000,
                true);
        try {
            var field = AuditLogArchivalWorker.class.getDeclaredField("self");
            field.setAccessible(true);
            field.set(worker, worker);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void archiveTenantUploadsThenPurges() {
        AuditLog row = sampleRow();
        when(auditLogRepository.findByCreatedAtBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(row))
                .thenReturn(List.of());
        when(archiveStorageService.uploadArchive(eq(tenantId), any(), any()))
                .thenReturn("archives/" + tenantId + "/audit/2026/07/file.jsonl.gz");
        when(auditLogRepository.deleteByIdIn(anyList())).thenReturn(1);

        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int purged = worker.archiveTenant(tenantId, cutoff);

        assertThat(purged).isEqualTo(1);
        ArgumentCaptor<String> filename = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(archiveStorageService).uploadArchive(eq(tenantId), filename.capture(), payload.capture());
        assertThat(filename.getValue()).endsWith(".jsonl.gz");
        assertThat(payload.getValue().length).isGreaterThan(0);
        verify(auditLogRepository).deleteByIdIn(List.of(row.getId()));
        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    void archiveTenantSkipsPurgeWhenS3Fails() {
        AuditLog row = sampleRow();
        when(auditLogRepository.findByCreatedAtBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(row));
        when(archiveStorageService.uploadArchive(eq(tenantId), any(), any()))
                .thenThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_ARCHIVE_UPLOAD_FAILED", "nope"));

        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        try {
            worker.archiveTenant(tenantId, cutoff);
        } catch (ApiException ignored) {
            // expected
        }

        verify(auditLogRepository, never()).deleteByIdIn(anyList());
        verify(auditLogRepository, never()).purgeArchivedByIdCsv(any());
    }

    @Test
    void nightlyRunSkipsWhenLockHeld() {
        when(jobLock.tryLock(eq(AuditLogArchivalWorker.JOB_LOCK_NAME), any())).thenReturn(false);

        worker.runNightlyArchival();

        verify(bootstrapJdbc, never()).listActiveTenantIds();
        verify(jobLock, never()).unlock(any());
    }

    @Test
    void nightlyRunArchivesEachTenantAndReleasesLock() {
        when(jobLock.tryLock(eq(AuditLogArchivalWorker.JOB_LOCK_NAME), any())).thenReturn(true);
        when(bootstrapJdbc.listActiveTenantIds()).thenReturn(List.of(tenantId));
        when(auditLogRepository.findByCreatedAtBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        worker.runNightlyArchival();

        verify(jobLock).unlock(AuditLogArchivalWorker.JOB_LOCK_NAME);
        verify(auditLogRepository, times(1)).findByCreatedAtBefore(any(Instant.class), any(Pageable.class));
    }

    @Test
    void nightlyRunRecordsMetricWhenTenantArchiveFails() {
        when(jobLock.tryLock(eq(AuditLogArchivalWorker.JOB_LOCK_NAME), any())).thenReturn(true);
        when(bootstrapJdbc.listActiveTenantIds()).thenReturn(List.of(tenantId));
        when(auditLogRepository.findByCreatedAtBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(sampleRow()));
        when(archiveStorageService.uploadArchive(eq(tenantId), any(), any()))
                .thenThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_ARCHIVE_UPLOAD_FAILED", "timeout"));

        worker.runNightlyArchival();

        verify(wmsMetrics).incrementAuditArchiveFailure(tenantId);
        verify(auditLogRepository, never()).deleteByIdIn(anyList());
        verify(jobLock).unlock(AuditLogArchivalWorker.JOB_LOCK_NAME);
    }

    @Test
    void gzipJsonLinesProducesGzipMagic() {
        byte[] gz = worker.gzipJsonLines(List.of(sampleRow()));
        assertThat(gz[0]).isEqualTo((byte) 0x1f);
        assertThat(gz[1]).isEqualTo((byte) 0x8b);
    }

    private AuditLog sampleRow() {
        AuditLog row = new AuditLog();
        row.setId(UUID.fromString("c0000000-0000-4000-8000-000000000099"));
        row.setTenantId(tenantId);
        row.setActorUserId(UUID.fromString("c0000000-0000-4000-8000-000000000010"));
        row.setAction("UPDATE_ROLE");
        row.setEntityType("USER");
        row.setEntityId(UUID.fromString("c0000000-0000-4000-8000-000000000011"));
        row.setCreatedAt(Instant.now().minus(120, ChronoUnit.DAYS));
        row.setUpdatedAt(row.getCreatedAt());
        return row;
    }
}
