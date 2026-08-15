package com.invsys.core.security;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.subscription.AppModule;
import com.invsys.service.TenantSubscriptionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Gatekeeper: blocks controller entry when the tenant lacks the required commercial module.
 */
@Aspect
@Component
public class RequireModuleAspect {

    private final TenantSubscriptionService tenantSubscriptionService;

    public RequireModuleAspect(TenantSubscriptionService tenantSubscriptionService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
    }

    @Around("@annotation(com.invsys.core.security.RequireModule) || @within(com.invsys.core.security.RequireModule)")
    public Object enforceModule(ProceedingJoinPoint joinPoint) throws Throwable {
        RequireModule requireModule = resolveAnnotation(joinPoint);
        if (requireModule == null) {
            return joinPoint.proceed();
        }

        AppModule required = requireModule.value();
        UUID tenantId = TenantContext.requireTenantId();
        if (!tenantSubscriptionService.isModuleEnabled(tenantId, required)) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "MODULE_LOCKED",
                    "This feature requires a subscription upgrade.");
        }
        return joinPoint.proceed();
    }

    private static RequireModule resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireModule onMethod = AnnotationUtils.findAnnotation(method, RequireModule.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequireModule.class);
    }
}
