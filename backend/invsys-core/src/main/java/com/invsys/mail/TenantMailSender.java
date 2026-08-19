package com.invsys.mail;

import com.invsys.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Resolves SMTP: tenant settings first (Mailpit in local docker), then the
 * optional platform {@link JavaMailSender}.
 */
@Component
public class TenantMailSender {

    private static final Logger log = LoggerFactory.getLogger(TenantMailSender.class);
    private static final DateTimeFormatter ALERT_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final ObjectProvider<JavaMailSender> platformSender;
    private final SettingsService settingsService;
    private final String fallbackFrom;

    public TenantMailSender(
            ObjectProvider<JavaMailSender> platformSender,
            SettingsService settingsService,
            @org.springframework.beans.factory.annotation.Value("${invsys.alerts.from-email:noreply@invsys.local}")
            String fallbackFrom) {
        this.platformSender = platformSender;
        this.settingsService = settingsService;
        this.fallbackFrom = fallbackFrom;
    }

    public JavaMailSender current() {
        Map<String, Object> settings = tenantSettingsOrEmpty();
        JavaMailSender tenant = TenantSmtpFactory.fromSettings(settings);
        if (tenant != null) {
            return tenant;
        }
        return platformSender.getIfAvailable();
    }

    public String fromAddress() {
        return TenantSmtpFactory.fromAddress(tenantSettingsOrEmpty(), fallbackFrom);
    }

    /**
     * Warns the account holder about a sign-in from a new IP or region.
     * Missing SMTP is a no-op so login itself never fails.
     */
    public boolean sendNewLoginAlert(String toEmail, String ip, String location, Instant time) {
        if (toEmail == null || toEmail.isBlank()) {
            return false;
        }
        JavaMailSender mailSender = current();
        if (mailSender == null) {
            log.warn("SMTP not configured — new-login alert skipped to={}", toEmail);
            return false;
        }
        Instant observed = time != null ? time : Instant.now();
        String where = location == null || location.isBlank() ? "Unknown Region" : location;
        String fromIp = ip == null || ip.isBlank() ? "unknown" : ip;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress());
            message.setTo(toEmail.trim());
            message.setSubject("New sign-in to weGrowStock");
            message.setText("""
                    We noticed a new sign-in to your weGrowStock account.

                    IP: %s
                    Location: %s
                    Time: %s

                    If this was you, no action is needed. If you do not recognize this sign-in, reset your password and contact your workspace admin.
                    """.formatted(fromIp, where, ALERT_TIME.format(observed)));
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("New-login alert failed to={}: {}", toEmail, ex.getMessage());
            return false;
        }
    }

    private Map<String, Object> tenantSettingsOrEmpty() {
        try {
            return settingsService.getSettings();
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }
}
