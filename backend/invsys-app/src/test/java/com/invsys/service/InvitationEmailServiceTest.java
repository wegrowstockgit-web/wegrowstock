package com.invsys.service;

import com.invsys.mail.TenantMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationEmailServiceTest {

    @Mock TenantMailSender tenantMailSender;
    @Mock JavaMailSender mailSender;
    @Mock MimeMessage mimeMessage;

    InvitationEmailService service;

    @BeforeEach
    void setUp() {
        service = new InvitationEmailService(tenantMailSender, "http://localhost:3000/");
    }

    @Test
    void inviteUrlStripsTrailingSlash() {
        assertThat(service.inviteUrl("abc-token")).isEqualTo("http://localhost:3000/invite/abc-token");
    }

    @Test
    void renderHtmlEmbedsInviteUrlAndBranding() {
        String html = service.renderHtml("http://localhost:3000/invite/tok-1");
        assertThat(html)
                .contains("weGrowStock WMS Workspace Invitation")
                .contains("weGrowStock Warehouse Management")
                .contains("weGrowStock. All rights reserved")
                .doesNotContain("InvSys")
                .contains("Accept Invitation &amp; Get Started")
                .contains("href=\"http://localhost:3000/invite/tok-1\"")
                .contains("7 days")
                .doesNotContain("Prefix");
    }

    @Test
    void sendInvitationUsesMimeMessageHelper() throws Exception {
        when(tenantMailSender.current()).thenReturn(mailSender);
        when(tenantMailSender.fromAddress()).thenReturn("noreply@invsys.local");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        boolean ok = service.sendInvitation("user@example.com", "http://localhost:3000/invite/t");
        assertThat(ok).isTrue();
        verify(mailSender).send(any(MimeMessage.class));
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue()).isSameAs(mimeMessage);
    }

    @Test
    void sendInvitationWithoutSmtpFails() {
        when(tenantMailSender.current()).thenReturn(null);
        InvitationEmailService noSmtp = new InvitationEmailService(tenantMailSender, "http://localhost:3000");
        assertThat(noSmtp.sendInvitation("user@example.com", "http://localhost:3000/invite/t")).isFalse();
    }

    @Test
    void sendInvitationRejectsBlankTargets() {
        assertThat(service.sendInvitation(" ", "http://localhost:3000/invite/t")).isFalse();
        assertThat(service.sendInvitation("user@example.com", " ")).isFalse();
    }

    @Test
    void magicAndWelcomeUrlsPointAtFrontend() {
        assertThat(service.magicLoginUrl("tok-9")).isEqualTo("http://localhost:3000/login?magic=tok-9");
        assertThat(service.wholesaleWelcomeUrl("tok-9"))
                .isEqualTo("http://localhost:3000/showroom/login?magic=tok-9");
    }

    @Test
    void sendWholesaleWelcomeWithoutSmtpFails() {
        when(tenantMailSender.current()).thenReturn(null);
        InvitationEmailService noSmtp = new InvitationEmailService(tenantMailSender, "http://localhost:3000");
        assertThat(noSmtp.sendWholesaleWelcome("buyer@example.com",
                "http://localhost:3000/showroom/login?magic=abc")).isFalse();
        assertThat(noSmtp.sendMagicLink("buyer@example.com",
                "http://localhost:3000/login?magic=abc")).isFalse();
    }
}
