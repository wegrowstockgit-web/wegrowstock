package com.invsys.mail;

import com.invsys.service.SettingsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves SMTP: tenant settings first (Mailpit in local docker), then the
 * optional platform {@link JavaMailSender}.
 */
@Component
public class TenantMailSender {

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

    private Map<String, Object> tenantSettingsOrEmpty() {
        try {
            return settingsService.getSettings();
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }
}
