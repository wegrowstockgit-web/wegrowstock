package com.invsys.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSmtpFactoryTest {

    @Test
    void fromSettingsRequiresHost() {
        assertThat(TenantSmtpFactory.fromSettings(null)).isNull();
        assertThat(TenantSmtpFactory.fromSettings(Map.of("smtp_port", 1025))).isNull();
        assertThat(TenantSmtpFactory.fromSettings(Map.of("smtp_host", "  "))).isNull();
    }

    @Test
    void fromSettingsBuildsMailpitSender() {
        JavaMailSenderImpl sender = (JavaMailSenderImpl) TenantSmtpFactory.fromSettings(Map.of(
                "smtp_host", "mailpit",
                "smtp_port", "1025",
                "smtp_auth", false,
                "smtp_from", "noreply@demo.test"));
        assertThat(sender).isNotNull();
        assertThat(sender.getHost()).isEqualTo("mailpit");
        assertThat(sender.getPort()).isEqualTo(1025);
        assertThat(sender.getJavaMailProperties().get("mail.smtp.auth")).isEqualTo("false");
        assertThat(TenantSmtpFactory.fromAddress(Map.of("smtp_from", "noreply@demo.test"), "x"))
                .isEqualTo("noreply@demo.test");
        assertThat(TenantSmtpFactory.fromAddress(Map.of(), "fallback")).isEqualTo("fallback");
        assertThat(TenantSmtpFactory.intValue("nope", 25)).isEqualTo(25);
        assertThat(TenantSmtpFactory.boolValue(Boolean.TRUE, false)).isTrue();
        assertThat(TenantSmtpFactory.boolValue("true", false)).isTrue();
        assertThat(TenantSmtpFactory.boolValue(null, true)).isTrue();
    }
}
