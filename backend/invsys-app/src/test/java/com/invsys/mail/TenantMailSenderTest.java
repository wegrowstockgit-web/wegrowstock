package com.invsys.mail;

import com.invsys.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantMailSenderTest {

    @Mock ObjectProvider<JavaMailSender> platformSender;
    @Mock SettingsService settingsService;
    @Mock JavaMailSender platform;

    @Test
    void prefersTenantSmtpOverPlatform() {
        when(settingsService.getSettings()).thenReturn(Map.of(
                "smtp_host", "mailpit",
                "smtp_port", 1025,
                "smtp_from", "noreply@demo.test"));
        TenantMailSender sender = new TenantMailSender(platformSender, settingsService, "fallback@invsys.local");
        JavaMailSenderImpl current = (JavaMailSenderImpl) sender.current();
        assertThat(current.getHost()).isEqualTo("mailpit");
        assertThat(current.getPort()).isEqualTo(1025);
        assertThat(sender.fromAddress()).isEqualTo("noreply@demo.test");
    }

    @Test
    void fallsBackToPlatformWhenTenantHasNoHost() {
        when(settingsService.getSettings()).thenReturn(Map.of("currency", "USD"));
        when(platformSender.getIfAvailable()).thenReturn(platform);
        TenantMailSender sender = new TenantMailSender(platformSender, settingsService, "fallback@invsys.local");
        assertThat(sender.current()).isSameAs(platform);
        assertThat(sender.fromAddress()).isEqualTo("fallback@invsys.local");
    }

    @Test
    void treatsMissingTenantContextAsEmptySettings() {
        when(settingsService.getSettings()).thenThrow(new IllegalStateException("no tenant"));
        when(platformSender.getIfAvailable()).thenReturn(null);
        TenantMailSender sender = new TenantMailSender(platformSender, settingsService, "fallback@invsys.local");
        assertThat(sender.current()).isNull();
        assertThat(sender.fromAddress()).isEqualTo("fallback@invsys.local");
    }
}
