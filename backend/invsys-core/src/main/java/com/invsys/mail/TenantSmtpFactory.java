package com.invsys.mail;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Map;
import java.util.Properties;

/**
 * Builds a per-tenant {@link JavaMailSender} from {@code tenant_settings} SMTP keys.
 */
public final class TenantSmtpFactory {

    private TenantSmtpFactory() {
    }

    public static JavaMailSender fromSettings(Map<String, Object> settings) {
        if (settings == null) {
            return null;
        }
        String host = stringValue(settings.get("smtp_host"));
        if (host == null || host.isBlank()) {
            return null;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(intValue(settings.get("smtp_port"), 1025));
        String username = stringValue(settings.get("smtp_username"));
        if (username != null && !username.isBlank()) {
            sender.setUsername(username);
        }
        String password = stringValue(settings.get("smtp_password"));
        if (password != null) {
            sender.setPassword(password);
        }
        boolean auth = boolValue(settings.get("smtp_auth"), username != null && !username.isBlank());
        boolean startTls = boolValue(settings.get("smtp_starttls"), false);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", Boolean.toString(auth));
        props.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        props.put("mail.smtp.connectiontimeout", "4000");
        props.put("mail.smtp.timeout", "8000");
        return sender;
    }

    public static String fromAddress(Map<String, Object> settings, String fallback) {
        String from = settings == null ? null : stringValue(settings.get("smtp_from"));
        if (from != null && !from.isBlank()) {
            return from.trim();
        }
        return fallback;
    }

    static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static boolean boolValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
