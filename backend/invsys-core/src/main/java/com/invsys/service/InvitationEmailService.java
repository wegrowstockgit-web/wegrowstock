package com.invsys.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.invsys.domain.Invitation;

/**
 * Builds and sends compact HTML invitation emails via {@link MimeMessageHelper}.
 */
@Service
public class InvitationEmailService {

    private static final Logger log = LoggerFactory.getLogger(InvitationEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendUrl;

    public InvitationEmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${invsys.alerts.from-email:noreply@invsys.local}") String fromAddress,
            @Value("${invsys.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    public String inviteUrl(String rawToken) {
        return frontendUrl + "/invite/" + rawToken;
    }

    public boolean sendInvitation(String toEmail, String inviteUrl) {
        if (toEmail == null || toEmail.isBlank() || inviteUrl == null || inviteUrl.isBlank()) {
            return false;
        }
        String html = renderHtml(inviteUrl);
        if (mailSender == null) {
            log.info("SMTP not configured — invitation email logged to={} url={}", toEmail, inviteUrl);
            return true;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail.trim());
            helper.setSubject("Join InvSys WMS");
            helper.setText(plainTextFallback(inviteUrl), html);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Invitation email dispatch failed to={}: {}", toEmail, ex.getMessage());
            return false;
        }
    }

    String renderHtml(String inviteUrl) {
        String safeUrl = inviteUrl
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Join InvSys WMS</title>
                </head>
                <body style="margin:0; padding:0; font-family:'Inter', system-ui, sans-serif; background-color:#f8fafc; color:#0f172a;">
                  <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color:#f8fafc; padding:48px 0;">
                    <tr>
                      <td align="center">
                        <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width:540px; background-color:#ffffff; border-radius:8px; border:1px solid #e2e8f0; overflow:hidden; box-shadow:0 1px 3px rgba(15,23,42,0.08);">
                          <tr>
                            <td style="background-color:#1d70cb; padding:32px; text-align:center;">
                              <h1 style="margin:0; color:#ffffff; font-size:24px; font-weight:700; letter-spacing:-0.025em;">InvSys WMS Workspace Invitation</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:40px 32px;">
                              <p style="margin:0 0 16px 0; font-size:16px; line-height:24px;">Hello,</p>
                              <p style="margin:0 0 24px 0; font-size:15px; line-height:24px; color:#64748b;">You have been invited to join your organization's secure logistics workspace on the InvSys Warehouse Management platform.</p>
                              <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin:32px 0;">
                                <tr>
                                  <td align="center">
                                    <a href="%s" style="display:inline-block; background-color:#1d70cb; color:#ffffff; font-size:15px; font-weight:600; text-decoration:none; padding:12px 32px; border-radius:6px; box-shadow:0 2px 4px rgba(29,112,203,0.2);">Accept Invitation &amp; Get Started</a>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:0 0 8px 0; font-size:13px; line-height:20px; color:#94a3b8; text-align:center;">This secure invitation link will remain valid for exactly 7 days.</p>
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
                """.formatted(safeUrl);
    }

    private static String plainTextFallback(String inviteUrl) {
        return """
                You have been invited to join InvSys WMS.

                Accept your invitation:
                %s

                This link remains valid for 7 days.
                """.formatted(inviteUrl);
    }
}
