package com.invsys.core.security;

import com.invsys.domain.AuditLog;
import com.invsys.mail.TenantMailSender;
import com.invsys.repository.AuditLogRepository;
import com.invsys.service.AuditService;
import com.invsys.service.PlatformAlertService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginSecurityServiceTest {

    @Mock AuditService auditService;
    @Mock AuditLogRepository auditLogRepository;
    @Mock PlatformAlertService platformAlertService;
    @Mock TenantMailSender tenantMailSender;

    private final GeoIpService geoIpService = new GeoIpService();
    private LoginSecurityService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void bindTenant() {
        service = new LoginSecurityService(
                auditService, auditLogRepository, geoIpService, platformAlertService, tenantMailSender);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void firstSuccessfulLoginRecordsWithoutAlert() {
        when(auditLogRepository.findByTenantIdAndEntityIdAndActionOrderByCreatedAtDesc(
                eq(tenantId), eq(userId), eq(LoginSecurityService.ACTION_LOGIN_SUCCESS), any(Pageable.class)))
                .thenReturn(List.of());

        service.afterSuccessfulLogin(userId, "picker@demo.test", "203.0.113.40", "Dallas, TX, US");

        verify(auditService).record(
                eq(LoginSecurityService.ACTION_LOGIN_SUCCESS),
                eq("USER"),
                eq(userId),
                any());
        verify(platformAlertService, never()).raise(any(), any(), any(), any(), any());
        verify(tenantMailSender, never()).sendNewLoginAlert(any(), any(), any(), any());
    }

    @Test
    void newLocationRaisesWarningAndEmailsUser() {
        AuditLog previous = new AuditLog();
        previous.setDiff(Map.of("ip", "203.0.113.40", "location", "Dallas, TX, US"));
        when(auditLogRepository.findByTenantIdAndEntityIdAndActionOrderByCreatedAtDesc(
                eq(tenantId), eq(userId), eq(LoginSecurityService.ACTION_LOGIN_SUCCESS), any(Pageable.class)))
                .thenReturn(List.of(previous));

        service.afterSuccessfulLogin(userId, "picker@demo.test", "198.51.100.45", "London, England, GB");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(platformAlertService).raise(
                eq(LoginSecurityService.ALERT_NEW_LOGIN_LOCATION),
                eq("WARNING"),
                eq(userId.toString()),
                eq("New sign-in from London, England, GB"),
                details.capture());
        assertThat(details.getValue()).containsEntry("ip", "198.51.100.45");
        assertThat(details.getValue()).containsEntry("previousIp", "203.0.113.40");
        verify(tenantMailSender).sendNewLoginAlert(
                eq("picker@demo.test"),
                eq("198.51.100.45"),
                eq("London, England, GB"),
                any(Instant.class));
        verify(auditService).record(
                eq(LoginSecurityService.ACTION_LOGIN_SUCCESS),
                eq("USER"),
                eq(userId),
                any());
    }

    @Test
    void blockedCidrWritesStructuredAuditDiff() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> diff = ArgumentCaptor.forClass(Map.class);

        service.recordLoginBlockedCidr(userId, "203.0.113.40", "Dallas, TX, US");

        verify(auditService).record(
                eq(LoginSecurityService.ACTION_LOGIN_BLOCKED_CIDR),
                eq("USER"),
                eq(userId),
                diff.capture());
        assertThat(diff.getValue().get("ip")).isEqualTo("203.0.113.40");
        assertThat(diff.getValue().get("location")).isEqualTo("Dallas, TX, US");
        assertThat(String.valueOf(diff.getValue().get("detail")))
                .contains("Blocked off-network login attempt from IP: 203.0.113.40");
    }
}
