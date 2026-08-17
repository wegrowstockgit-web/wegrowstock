package com.invsys.integration.alerts;

import com.invsys.mail.TenantMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailAlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailAlertDispatcher.class);

    private final TenantMailSender tenantMailSender;

    public SmtpEmailAlertDispatcher(TenantMailSender tenantMailSender) {
        this.tenantMailSender = tenantMailSender;
    }

    public boolean dispatch(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank()) {
            return false;
        }
        JavaMailSender mailSender = tenantMailSender.current();
        if (mailSender == null) {
            log.warn("SMTP not configured — email alert skipped to={} subject={}", toEmail, subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(tenantMailSender.fromAddress());
            message.setTo(toEmail.trim());
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Email alert dispatch failed to={}: {}", toEmail, ex.getMessage());
            return false;
        }
    }
}
