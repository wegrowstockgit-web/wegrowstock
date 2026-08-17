package com.invsys.service;

import com.invsys.mail.TenantMailSender;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Builds and sends compact HTML invitation emails via {@link MimeMessageHelper}.
 */
@Service
public class InvitationEmailService {

    private static final Logger log = LoggerFactory.getLogger(InvitationEmailService.class);

    private final TenantMailSender tenantMailSender;
    private final String frontendUrl;

    public InvitationEmailService(
            TenantMailSender tenantMailSender,
            @Value("${invsys.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.tenantMailSender = tenantMailSender;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    public String inviteUrl(String rawToken) {
        return frontendUrl + "/invite/" + rawToken;
    }

    public String magicLoginUrl(String rawToken) {
        return frontendUrl + "/login?magic=" + rawToken;
    }

    public String wholesaleWelcomeUrl(String rawToken) {
        return frontendUrl + "/showroom/login?magic=" + rawToken;
    }

    public boolean sendMagicLink(String toEmail, String loginUrl) {
        return dispatch(
                toEmail,
                loginUrl,
                "Your weGrowStock login link",
                "Sign in to weGrowStock",
                "Click the button below to sign in. This secure link expires in 15 minutes.",
                "Sign in",
                "This secure login link remains valid for 15 minutes.");
    }

    public boolean sendWholesaleWelcome(String toEmail, String loginUrl) {
        return dispatch(
                toEmail,
                loginUrl,
                "Welcome! Click here to access your wholesale account",
                "Welcome to wholesale",
                "Your wholesale account is ready. Click below to access your B2B catalog and pricing.",
                "Access your wholesale account",
                "This welcome link remains valid for 15 minutes.");
    }

    public boolean sendInvitation(String toEmail, String inviteUrl) {
        if (toEmail == null || toEmail.isBlank() || inviteUrl == null || inviteUrl.isBlank()) {
            return false;
        }
        return dispatch(
                toEmail,
                inviteUrl,
                "Join InvSys WMS",
                "InvSys WMS Workspace Invitation",
                "You have been invited to join your organization's secure logistics workspace on the InvSys Warehouse Management platform.",
                "Accept Invitation & Get Started",
                "This secure invitation link will remain valid for exactly 7 days.");
    }

    private boolean dispatch(String toEmail,
                             String actionUrl,
                             String subject,
                             String heading,
                             String body,
                             String cta,
                             String expiryNote) {
        if (toEmail == null || toEmail.isBlank() || actionUrl == null || actionUrl.isBlank()) {
            return false;
        }
        String html = renderActionHtml(actionUrl, heading, body, cta, expiryNote);
        JavaMailSender mailSender = tenantMailSender.current();
        if (mailSender == null) {
            log.warn("SMTP not configured — email not sent to {} ({})", redactEmail(toEmail), subject);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(tenantMailSender.fromAddress());
            helper.setTo(toEmail.trim());
            helper.setSubject(subject);
            helper.setText(plainTextFallback(actionUrl, body, expiryNote), html);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Email dispatch failed to={}: {}", toEmail, ex.getMessage());
            return false;
        }
    }

    String renderHtml(String inviteUrl) {
        return renderActionHtml(
                inviteUrl,
                "InvSys WMS Workspace Invitation",
                "You have been invited to join your organization's secure logistics workspace on the InvSys Warehouse Management platform.",
                "Accept Invitation & Get Started",
                "This secure invitation link will remain valid for exactly 7 days.");
    }

    String renderActionHtml(String actionUrl, String heading, String body, String cta, String expiryNote) {
        String safeUrl = escapeHtml(actionUrl);
        String safeHeading = escapeHtml(heading);
        String safeBody = escapeHtml(body);
        String safeCta = escapeHtml(cta);
        String safeExpiry = escapeHtml(expiryNote);
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0; padding:0; font-family:'Inter', system-ui, sans-serif; background-color:#f8fafc; color:#0f172a;">
                  <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color:#f8fafc; padding:48px 0;">
                    <tr>
                      <td align="center">
                        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width:540px; background-color:#ffffff; border-radius:8px; border:1px solid #e2e8f0; overflow:hidden; box-shadow:0 1px 3px rgba(15,23,42,0.08);">
                          <tr>
                            <td style="background-color:#1d70cb; padding:32px; text-align:center;">
                              <h1 style="margin:0; color:#ffffff; font-size:24px; font-weight:700; letter-spacing:-0.025em;">%s</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:40px 32px;">
                              <p style="margin:0 0 16px 0; font-size:16px; line-height:24px;">Hello,</p>
                              <p style="margin:0 0 24px 0; font-size:15px; line-height:24px; color:#64748b;">%s</p>
                              <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin:32px 0;">
                                <tr>
                                  <td align="center">
                                    <a href="%s" style="display:inline-block; background-color:#1d70cb; color:#ffffff; font-size:15px; font-weight:600; text-decoration:none; padding:12px 32px; border-radius:6px; box-shadow:0 2px 4px rgba(29,112,203,0.2);">%s</a>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:0 0 8px 0; font-size:13px; line-height:20px; color:#94a3b8; text-align:center;">%s</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="background-color:#f1f5f9; padding:16px 32px; border-top:1px solid #e2e8f0; text-align:center;">
                              <p style="margin:0; font-size:12px; color:#94a3b8; line-height:18px;">&copy; 2026 InvSys Logistics Systems Inc. All rights reserved.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(safeHeading, safeHeading, safeBody, safeUrl, safeCta, safeExpiry);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    static String redactEmail(String email) {
        if (email == null || email.isBlank()) {
            return "(blank)";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String plainTextFallback(String actionUrl, String body, String expiryNote) {
        return """
                %s

                %s

                %s
                """.formatted(body, actionUrl, expiryNote);
    }
}
