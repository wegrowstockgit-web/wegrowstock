package com.invsys.core.security;

import com.invsys.domain.AuditLog;
import com.invsys.mail.TenantMailSender;
import com.invsys.repository.AuditLogRepository;
import com.invsys.service.AuditService;
import com.invsys.service.PlatformAlertService;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IAM login auditing: persist auth events, detect new IP/location, and notify
 * the tenant user plus the platform alert inbox. Failures never fail the login.
 */
@Service
public class LoginSecurityService {

    public static final String ACTION_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String ACTION_LOGIN_BLOCKED_CIDR = "LOGIN_BLOCKED_CIDR";
    public static final String ALERT_NEW_LOGIN_LOCATION = "NEW_LOGIN_LOCATION";
    public static final String ENTITY_USER = "USER";

    private static final Logger log = LoggerFactory.getLogger(LoginSecurityService.class);

    private final AuditService auditService;
    private final AuditLogRepository auditLogRepository;
    private final GeoIpService geoIpService;
    private final PlatformAlertService platformAlertService;
    private final TenantMailSender tenantMailSender;

    public LoginSecurityService(AuditService auditService,
                                AuditLogRepository auditLogRepository,
                                GeoIpService geoIpService,
                                PlatformAlertService platformAlertService,
                                TenantMailSender tenantMailSender) {
        this.auditService = auditService;
        this.auditLogRepository = auditLogRepository;
        this.geoIpService = geoIpService;
        this.platformAlertService = platformAlertService;
        this.tenantMailSender = tenantMailSender;
    }

    /**
     * Compare against the previous successful login, then record this one.
     */
    public void afterSuccessfulLogin(UUID userId, String email, String ip, String location) {
        detectNewLoginLocation(userId, email, ip, location);
        recordLoginSuccess(userId, ip, location);
    }

    public void recordLoginSuccess(UUID userId, String ip, String location) {
        if (userId == null) {
            return;
        }
        String detail = "IP: " + ip + " | Location: " + location;
        record(ACTION_LOGIN_SUCCESS, userId, authDiff(ip, location, detail));
    }

    public void recordLoginBlockedCidr(UUID userId, String ip, String location) {
        UUID entityId = userId != null ? userId : TenantContext.getUserId().orElse(null);
        if (entityId == null) {
            return;
        }
        String detail = "Blocked off-network login attempt from IP: " + ip + " | Location: " + location;
        record(ACTION_LOGIN_BLOCKED_CIDR, entityId, authDiff(ip, location, detail));
    }

    void detectNewLoginLocation(UUID userId, String email, String ip, String location) {
        if (userId == null) {
            return;
        }
        try {
            UUID tenantId = TenantContext.requireTenantId();
            List<AuditLog> previous = auditLogRepository
                    .findByTenantIdAndEntityIdAndActionOrderByCreatedAtDesc(
                            tenantId, userId, ACTION_LOGIN_SUCCESS, PageRequest.of(0, 1));
            if (previous.isEmpty()) {
                return;
            }
            AuditLog last = previous.getFirst();
            if (!geoIpService.isNewNetworkOrLocation(last.getDiff(), ip, location)) {
                return;
            }
            String previousIp = stringValue(last.getDiff() == null ? null : last.getDiff().get("ip"));
            String previousLocation = stringValue(last.getDiff() == null ? null : last.getDiff().get("location"));
            Instant observedAt = Instant.now();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("userId", userId.toString());
            details.put("email", email);
            details.put("ip", ip);
            details.put("location", location);
            details.put("previousIp", previousIp);
            details.put("previousLocation", previousLocation);
            details.put("observedAt", observedAt.toString());
            platformAlertService.raise(
                    ALERT_NEW_LOGIN_LOCATION,
                    "WARNING",
                    userId.toString(),
                    "New sign-in from " + location,
                    details);
            tenantMailSender.sendNewLoginAlert(email, ip, location, observedAt);
        } catch (RuntimeException ex) {
            log.warn("New-login location detection failed userId={}: {}", userId, ex.getMessage());
        }
    }

    private void record(String action, UUID userId, Map<String, Object> diff) {
        try {
            TenantContext.setUserId(userId);
            auditService.record(action, ENTITY_USER, userId, diff);
        } catch (RuntimeException ex) {
            log.warn("Login audit record failed action={} userId={}: {}", action, userId, ex.getMessage());
        }
    }

    private static Map<String, Object> authDiff(String ip, String location, String detail) {
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("ip", ip);
        diff.put("location", location);
        diff.put("detail", detail);
        diff.put("summary", detail);
        return diff;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
