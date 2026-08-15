package com.invsys.core.security;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.subscription.AppModule;
import com.invsys.service.TenantSubscriptionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequireModuleAspectTest {

    @Mock TenantSubscriptionService tenantSubscriptionService;
    @Mock ProceedingJoinPoint joinPoint;
    @Mock MethodSignature methodSignature;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @RequireModule(AppModule.FINTECH)
    static class SampleController {
        public void dashboard() {
        }
    }

    @Test
    void allowsWhenModuleEnabled() throws Throwable {
        RequireModuleAspect aspect = new RequireModuleAspect(tenantSubscriptionService);
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        Method method = SampleController.class.getMethod("dashboard");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new SampleController());
        when(tenantSubscriptionService.isModuleEnabled(tenantId, AppModule.FINTECH)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        assertThat(aspect.enforceModule(joinPoint)).isEqualTo("ok");
        verify(joinPoint).proceed();
    }

    @Test
    void locksWhenModuleDisabled() throws Throwable {
        RequireModuleAspect aspect = new RequireModuleAspect(tenantSubscriptionService);
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        Method method = SampleController.class.getMethod("dashboard");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new SampleController());
        when(tenantSubscriptionService.isModuleEnabled(tenantId, AppModule.FINTECH)).thenReturn(false);

        assertThatThrownBy(() -> aspect.enforceModule(joinPoint))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(api.getCode()).isEqualTo("MODULE_LOCKED");
                });
    }
}
