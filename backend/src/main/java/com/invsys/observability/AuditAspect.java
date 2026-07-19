package com.invsys.observability;

import com.invsys.common.MdcLoggingFilter;
import com.invsys.common.MdcSupport;
import com.invsys.domain.User;
import com.invsys.service.AuditService;
import com.invsys.service.UserManagementService;
import com.invsys.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application-level audit trail: captures actor, action, entity, request id, and arg diffs.
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(auditable)")
    public Object aroundAuditable(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        UUID actorBefore = resolveActorUserId();
        String requestId = resolveRequestId();
        Map<String, Object> argDiff = serializeArgs(joinPoint);

        Object result = joinPoint.proceed();

        try {
            if (TenantContext.getTenantId().isEmpty()) {
                return result;
            }
            UUID entityId = resolveEntityId(result, joinPoint.getArgs());
            if (entityId == null) {
                entityId = UUID.fromString("00000000-0000-4000-8000-000000000000");
            }
            UUID actor = TenantContext.getUserId().orElse(actorBefore);

            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("source", "spring_aop");
            diff.put("requestId", requestId);
            diff.put("actorUserId", actor != null ? actor.toString() : null);
            diff.put("method", joinPoint.getSignature().toShortString());
            diff.put("args", argDiff);
            if (result != null) {
                diff.put("resultType", result.getClass().getSimpleName());
            }
            auditService.record(auditable.action(), auditable.entityType(), entityId, diff);
        } catch (Exception ex) {
            log.warn("AuditAspect failed to record {}: {}", auditable.action(), ex.getMessage());
        }
        return result;
    }

    private static UUID resolveActorUserId() {
        return TenantContext.getUserId().or(() -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return java.util.Optional.empty();
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof UUID uuid) {
                return java.util.Optional.of(uuid);
            }
            try {
                return java.util.Optional.of(UUID.fromString(String.valueOf(principal)));
            } catch (Exception ignored) {
                return java.util.Optional.empty();
            }
        }).orElse(null);
    }

    private static String resolveRequestId() {
        String fromMdc = MDC.get(MdcSupport.REQUEST_ID);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String legacy = MDC.get("requestId");
        if (legacy != null && !legacy.isBlank()) {
            return legacy;
        }
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String header = request.getHeader(MdcLoggingFilter.HEADER);
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        return null;
    }

    private static Map<String, Object> serializeArgs(ProceedingJoinPoint joinPoint) {
        Map<String, Object> args = new LinkedHashMap<>();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] values = joinPoint.getArgs();
        if (values == null) {
            return args;
        }
        for (int i = 0; i < values.length; i++) {
            String name = names != null && i < names.length ? names[i] : "arg" + i;
            Object value = values[i];
            if (value == null) {
                args.put(name, null);
            } else if (value instanceof UUID || value instanceof Number || value instanceof Boolean) {
                args.put(name, value.toString());
            } else if (value instanceof String s) {
                args.put(name, redactIfSensitive(name, s));
            } else if (value instanceof Enum<?> e) {
                args.put(name, e.name());
            } else {
                args.put(name, value.getClass().getSimpleName());
            }
        }
        return args;
    }

    private static String redactIfSensitive(String name, String value) {
        String key = name == null ? "" : name.toLowerCase();
        if (key.contains("password") || key.contains("secret") || key.contains("token")) {
            return "***";
        }
        return value;
    }

    private static UUID resolveEntityId(Object result, Object[] args) {
        if (result instanceof User user) {
            return user.getId();
        }
        if (result instanceof UserManagementService.InviteResult invite) {
            return invite.invitation().getId();
        }
        if (result instanceof UserManagementService.OrgScopeResult org) {
            return org.user().getId();
        }
        if (result instanceof UserManagementService.ResendInvitationResult resend) {
            return resend.invitationId();
        }
        if (result instanceof UUID uuid) {
            return uuid;
        }
        if (result != null) {
            try {
                Method getId = result.getClass().getMethod("getId");
                Object id = getId.invoke(result);
                if (id instanceof UUID uuid) {
                    return uuid;
                }
            } catch (ReflectiveOperationException ignored) {
                // fall through to args
            }
        }
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof UUID uuid) {
                    return uuid;
                }
            }
        }
        return null;
    }
}
