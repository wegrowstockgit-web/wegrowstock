package com.invsys.observability;

import com.invsys.domain.Invitation;
import com.invsys.domain.User;
import com.invsys.service.AuditService;
import com.invsys.service.UserManagementService;
import com.invsys.tenancy.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock AuditService auditService;
    @Mock ProceedingJoinPoint joinPoint;
    @Mock MethodSignature signature;

    AuditAspect aspect;
    UUID tenantId;
    UUID userId;

    @BeforeEach
    void setUp() {
        aspect = new AuditAspect(auditService);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);
        MDC.put("request_id", "req-audit-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsActionEntityRequestIdAndArgs() throws Throwable {
        Method method = Sample.class.getDeclaredMethod("updateUser", UUID.class, String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"userId", "role"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{userId, "ADMIN"});
        when(signature.toShortString()).thenReturn("Sample.updateUser(..)");

        User returned = new User();
        returned.setId(userId);
        when(joinPoint.proceed()).thenReturn(returned);

        Auditable auditable = method.getAnnotation(Auditable.class);
        Object out = aspect.aroundAuditable(joinPoint, auditable);

        assertThat(out).isSameAs(returned);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> diffCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq("UPDATE_USER"), eq("USER"), eq(userId), diffCaptor.capture());
        Map<String, Object> diff = diffCaptor.getValue();
        assertThat(diff.get("source")).isEqualTo("spring_aop");
        assertThat(diff.get("requestId")).isEqualTo("req-audit-1");
        assertThat(diff.get("args")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) diff.get("args");
        assertThat(args.get("role")).isEqualTo("ADMIN");
    }

    @Test
    void redactsPasswordArgs() throws Throwable {
        Method method = Sample.class.getDeclaredMethod("setPassword", String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"password"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"super-secret"});
        when(signature.toShortString()).thenReturn("Sample.setPassword(..)");
        when(joinPoint.proceed()).thenReturn(userId);

        Auditable auditable = method.getAnnotation(Auditable.class);
        aspect.aroundAuditable(joinPoint, auditable);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> diffCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq("UPDATE_USER"), eq("USER"), eq(userId), diffCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) diffCaptor.getValue().get("args");
        assertThat(args.get("password")).isEqualTo("***");
    }

    @Test
    void skipsWhenTenantContextMissing() throws Throwable {
        TenantContext.clear();
        Method method = Sample.class.getDeclaredMethod("updateUser", UUID.class, String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"userId", "role"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{userId, "ADMIN"});
        when(joinPoint.proceed()).thenReturn(userId);
        Auditable auditable = method.getAnnotation(Auditable.class);
        Object out = aspect.aroundAuditable(joinPoint, auditable);
        assertThat(out).isEqualTo(userId);
        verify(auditService, org.mockito.Mockito.never()).record(any(), any(), any(), any());
    }

    @Test
    void usesArgUuidWhenResultHasNoId() throws Throwable {
        Method method = Sample.class.getDeclaredMethod("voidish", UUID.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"userId"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{userId});
        when(signature.toShortString()).thenReturn("Sample.voidish(..)");
        when(joinPoint.proceed()).thenReturn(null);

        Auditable auditable = method.getAnnotation(Auditable.class);
        aspect.aroundAuditable(joinPoint, auditable);
        verify(auditService).record(eq("DEACTIVATE_USER"), eq("USER"), eq(userId), any());
    }

    @Test
    void extractsInvitationIdFromInviteResult() throws Throwable {
        Method method = Sample.class.getDeclaredMethod("invite");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{});
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(signature.toShortString()).thenReturn("Sample.invite(..)");

        Invitation invitation = new Invitation();
        invitation.setId(UUID.randomUUID());
        var result = new UserManagementService.InviteResult(invitation, "raw-token");
        when(joinPoint.proceed()).thenReturn(result);

        Auditable auditable = method.getAnnotation(Auditable.class);
        aspect.aroundAuditable(joinPoint, auditable);
        verify(auditService).record(eq("INVITE_USER"), eq("INVITATION"), eq(invitation.getId()), any());
    }

    @Test
    void extractsIdsFromOrgScopeAndResendResults() throws Throwable {
        Method orgMethod = Sample.class.getDeclaredMethod("orgScope");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{});
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(signature.toShortString()).thenReturn("Sample.orgScope(..)");
        User user = new User();
        user.setId(userId);
        when(joinPoint.proceed()).thenReturn(
                new UserManagementService.OrgScopeResult(user, java.util.List.of("ADMIN"), java.util.List.of()));
        aspect.aroundAuditable(joinPoint, orgMethod.getAnnotation(Auditable.class));
        verify(auditService).record(eq("UPDATE_USER"), eq("USER"), eq(userId), any());

        Method resendMethod = Sample.class.getDeclaredMethod("resend");
        UUID inviteId = UUID.randomUUID();
        when(joinPoint.proceed()).thenReturn(new UserManagementService.ResendInvitationResult(
                inviteId, "a@b.c", "VIEWER", java.time.Instant.now(), "http://x", true, java.util.List.of()));
        aspect.aroundAuditable(joinPoint, resendMethod.getAnnotation(Auditable.class));
        verify(auditService).record(eq("RESEND_INVITATION"), eq("INVITATION"), eq(inviteId), any());
    }

    @Test
    void readsRequestIdFromHttpHeaderWhenMdcEmpty() throws Throwable {
        MDC.clear();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "hdr-corr-77");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Method method = Sample.class.getDeclaredMethod("voidish", UUID.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"userId"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{userId});
        when(signature.toShortString()).thenReturn("Sample.voidish(..)");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.aroundAuditable(joinPoint, method.getAnnotation(Auditable.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> diffCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq("DEACTIVATE_USER"), eq("USER"), eq(userId), diffCaptor.capture());
        assertThat(diffCaptor.getValue().get("requestId")).isEqualTo("hdr-corr-77");
    }

    @Test
    void usesNilEntityWhenNoIdAvailable() throws Throwable {
        Method method = Sample.class.getDeclaredMethod("invite");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(null);
        when(signature.toShortString()).thenReturn("Sample.invite(..)");
        when(joinPoint.proceed()).thenReturn("no-id");

        aspect.aroundAuditable(joinPoint, method.getAnnotation(Auditable.class));
        verify(auditService).record(
                eq("INVITE_USER"),
                eq("INVITATION"),
                eq(UUID.fromString("00000000-0000-4000-8000-000000000000")),
                any());
    }

    @Test
    void survivesAuditServiceFailure() throws Throwable {
        Method method = Sample.class.getDeclaredMethod("voidish", UUID.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"userId"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{userId});
        when(signature.toShortString()).thenReturn("Sample.voidish(..)");
        when(joinPoint.proceed()).thenReturn(null);
        org.mockito.Mockito.doThrow(new RuntimeException("audit down"))
                .when(auditService).record(any(), any(), any(), any());

        Object out = aspect.aroundAuditable(joinPoint, method.getAnnotation(Auditable.class));
        assertThat(out).isNull();
    }

    static class Sample {
        @Auditable(action = "UPDATE_USER", entityType = "USER")
        User updateUser(UUID userId, String role) {
            return null;
        }

        @Auditable(action = "UPDATE_USER", entityType = "USER")
        UUID setPassword(String password) {
            return null;
        }

        @Auditable(action = "DEACTIVATE_USER", entityType = "USER")
        void voidish(UUID userId) {
        }

        @Auditable(action = "INVITE_USER", entityType = "INVITATION")
        UserManagementService.InviteResult invite() {
            return null;
        }

        @Auditable(action = "UPDATE_USER", entityType = "USER")
        UserManagementService.OrgScopeResult orgScope() {
            return null;
        }

        @Auditable(action = "RESEND_INVITATION", entityType = "INVITATION")
        UserManagementService.ResendInvitationResult resend() {
            return null;
        }
    }
}
