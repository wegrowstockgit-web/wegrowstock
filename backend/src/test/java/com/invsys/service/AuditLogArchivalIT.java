package com.invsys.service;

import com.invsys.AbstractIntegrationTest;
import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.AuditLog;
import com.invsys.media.ObjectStorage;
import com.invsys.repository.AuditLogRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogArchivalIT extends AbstractIntegrationTest {

    @Autowired AuthService authService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired AuditLogArchivalWorker archivalWorker;
    @Autowired ObjectStorage objectStorage;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void archivesAgedRowsToS3ThenPurgesViaSecurityDefiner() {
        String slug = "arc-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Archive Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        AuditLog aged = new AuditLog();
        aged.setTenantId(tenantId);
        aged.setActorUserId(owner.userId());
        aged.setAction("ARCHIVE_TEST");
        aged.setEntityType("USER");
        aged.setEntityId(owner.userId());
        aged.setDiff(Map.of("fixture", true));
        aged.setCreatedAt(Instant.now().minus(120, ChronoUnit.DAYS));
        aged.setUpdatedAt(aged.getCreatedAt());
        aged = auditLogRepository.saveAndFlush(aged);
        UUID agedId = aged.getId();

        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int purged = archivalWorker.archiveTenant(tenantId, cutoff);
        assertThat(purged).isGreaterThanOrEqualTo(1);

        TenantContext.setTenantId(tenantId);
        assertThat(auditLogRepository.findById(agedId)).isEmpty();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String prefix = "archives/" + tenantId + "/audit/"
                + String.format("%04d", today.getYear()) + "/"
                + String.format("%02d", today.getMonthValue()) + "/";
        String filename = AuditLogArchivalWorker.buildFilename(List.of(aged));
        assertThat(objectStorage.exists(prefix + filename)).isTrue();

        // Fresh rows under the retention window remain.
        List<AuditLog> recent = auditLogRepository.findByCreatedAtBefore(
                Instant.now().plus(1, ChronoUnit.DAYS), PageRequest.of(0, 5));
        assertThat(recent).isNotEmpty();
    }
}
