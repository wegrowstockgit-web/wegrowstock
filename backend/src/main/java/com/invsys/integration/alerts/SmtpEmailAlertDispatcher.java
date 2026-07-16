package com.invsys.integration.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailAlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailAlertDispatcher.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailAlertDispatcher(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${invsys.alerts.from-email:noreply@invsys.local}") String fromAddress) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.fromAddress = fromAddress;
    }

    public boolean dispatch(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank()) {
            return false;
        }
        if (mailSender == null) {
            log.info("SMTP not configured — email alert skipped to={} subject={}", toEmail, subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
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
