package com.invsys.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invsys.domain.PlatformAdmin;
import com.invsys.repository.PlatformAdminRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
public class PlatformAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuditAspect.class);

    private final JdbcTemplate jdbc;
    private final PlatformAdminRepository platformAdminRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlatformAuditAspect(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
                               PlatformAdminRepository platformAdminRepository) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
        this.platformAdminRepository = platformAdminRepository;
    }

    @Around("@annotation(platformAudit)")
    public Object around(ProceedingJoinPoint joinPoint, PlatformAudit platformAudit) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            record(joinPoint, platformAudit);
        } catch (Exception ex) {
            log.warn("PlatformAudit failed for {}: {}", platformAudit.action(), ex.getMessage());
        }
        return result;
    }

    private void record(ProceedingJoinPoint joinPoint, PlatformAudit platformAudit) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID adminId = null;
        String adminEmail = null;
        if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
            adminId = uuid;
            adminEmail = platformAdminRepository.findById(uuid).map(PlatformAdmin::getEmail).orElse(null);
        }
        if (adminId == null) {
            adminId = UUID.fromString("00000000-0000-4000-8000-000000000000");
        }

        UUID targetTenantId = resolveTenantId(joinPoint, platformAudit.tenantIdParam());
        Map<String, Object> diff = serializeArgs(joinPoint);
        String diffJson = objectMapper.writeValueAsString(diff);
        String ip = resolveClientIp();

        jdbc.update("""
                INSERT INTO platform_audit_logs (id, admin_id, admin_email, action, target_tenant_id, diff_json, ip_address, actor_type)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """,
                UUID.randomUUID(),
                adminId,
                adminEmail,
                platformAudit.action(),
                targetTenantId,
                diffJson,
                ip,
                actorType(platformAudit));
    }

    private static UUID resolveTenantId(ProceedingJoinPoint joinPoint, String paramName) {
        if (paramName == null || paramName.isBlank()) {
            return firstUuidArg(joinPoint.getArgs());
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] values = joinPoint.getArgs();
        if (names == null || values == null) {
            return firstUuidArg(values);
        }
        for (int i = 0; i < names.length; i++) {
            if (paramName.equals(names[i]) && values[i] instanceof UUID uuid) {
                return uuid;
            }
        }
        return firstUuidArg(values);
    }

    private static UUID firstUuidArg(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
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
                args.put(name, String.valueOf(value));
            }
        }
        return args;
    }

    private static String redactIfSensitive(String name, String value) {
        String key = name == null ? "" : name.toLowerCase();
        if (key.contains("password") || key.contains("secret") || key.contains("token") || key.contains("apikey")) {
            return "***";
        }
        return value;
    }

    private static String resolveClientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return null;
        }
        HttpServletRequest request = servletAttrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String actorType(PlatformAudit platformAudit) {
        if ("TENANT_IMPERSONATE".equals(platformAudit.action())) {
            return "PLATFORM_ADMIN_IMPERSONATION";
        }
        String configured = platformAudit.actorType();
        return configured == null || configured.isBlank() ? "PLATFORM_ADMIN" : configured;
    }
}
