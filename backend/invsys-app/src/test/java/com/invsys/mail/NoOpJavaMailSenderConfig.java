package com.invsys.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.io.InputStream;
import java.util.Properties;

/**
 * Platform {@link JavaMailSender} for integration tests so invoice dispatch
 * can succeed without a live SMTP server.
 */
@TestConfiguration
public class NoOpJavaMailSenderConfig {

    @Bean
    JavaMailSender javaMailSender() {
        return new NoOpJavaMailSender();
    }

    static final class NoOpJavaMailSender implements JavaMailSender {

        private static final Session SESSION = Session.getInstance(new Properties());

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(SESSION);
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(SESSION, contentStream);
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to parse MIME stream", ex);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            // test sink
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            // test sink
        }

        @Override
        public void send(MimeMessagePreparator mimeMessagePreparator) {
            try {
                MimeMessage message = createMimeMessage();
                mimeMessagePreparator.prepare(message);
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to prepare MIME message", ex);
            }
        }

        @Override
        public void send(MimeMessagePreparator... mimeMessagePreparators) {
            for (MimeMessagePreparator preparator : mimeMessagePreparators) {
                send(preparator);
            }
        }

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            // test sink
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            // test sink
        }
    }
}
