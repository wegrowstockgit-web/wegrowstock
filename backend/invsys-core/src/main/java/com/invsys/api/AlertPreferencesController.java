package com.invsys.api;

import com.invsys.core.common.ApiException;
import com.invsys.integration.alerts.IntegrationAlertService;
import com.invsys.media.MediaUrlValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/settings/alert-preferences")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class AlertPreferencesController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    public static final Set<String> SLACK_HOSTS = Set.of("hooks.slack.com", "hooks.slack-gov.com");

    private final IntegrationAlertService integrationAlertService;
    private final MediaUrlValidator mediaUrlValidator;

    public AlertPreferencesController(IntegrationAlertService integrationAlertService,
                                      MediaUrlValidator mediaUrlValidator) {
        this.integrationAlertService = integrationAlertService;
        this.mediaUrlValidator = mediaUrlValidator;
    }

    @GetMapping
    public IntegrationAlertService.AlertPreferencesDto get() {
        return integrationAlertService.getPreferences();
    }

    @PutMapping
    public IntegrationAlertService.AlertPreferencesDto update(@Valid @RequestBody UpdateRequest request) {
        validateEmail(request.alertEmail());
        validateSlackUrl(request.slackWebhookUrl());
        return integrationAlertService.updatePreferences(
                new IntegrationAlertService.UpdateAlertPreferencesRequest(
                        request.alertEmail(), request.slackWebhookUrl()));
    }

    @PostMapping("/test")
    public Map<String, Object> testAlert() {
        return integrationAlertService.sendTestAlert();
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (!EMAIL.matcher(email.trim()).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ALERT_EMAIL",
                    "IT contact email is not a valid email address");
        }
    }

    private void validateSlackUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        mediaUrlValidator.assertAllowedHttpsHost(url, SLACK_HOSTS, "INVALID_SLACK_URL");
    }

    public record UpdateRequest(
            @Size(max = 255) String alertEmail,
            @Size(max = 1024) String slackWebhookUrl
    ) {
    }
}
