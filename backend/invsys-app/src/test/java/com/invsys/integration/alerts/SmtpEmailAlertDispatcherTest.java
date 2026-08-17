package com.invsys.integration.alerts;

import com.invsys.mail.TenantMailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailAlertDispatcherTest {

    @Mock TenantMailSender tenantMailSender;
    @Mock JavaMailSender mailSender;

    @Test
    void dispatchSendsWhenTenantSmtpIsConfigured() {
        when(tenantMailSender.current()).thenReturn(mailSender);
        when(tenantMailSender.fromAddress()).thenReturn("noreply@demo.test");
        SmtpEmailAlertDispatcher dispatcher = new SmtpEmailAlertDispatcher(tenantMailSender);
        assertThat(dispatcher.dispatch("ops@demo.test", "Alert", "body")).isTrue();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void dispatchFailsWithoutSmtp() {
        when(tenantMailSender.current()).thenReturn(null);
        SmtpEmailAlertDispatcher dispatcher = new SmtpEmailAlertDispatcher(tenantMailSender);
        assertThat(dispatcher.dispatch("ops@demo.test", "Alert", "body")).isFalse();
        assertThat(dispatcher.dispatch(" ", "Alert", "body")).isFalse();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
