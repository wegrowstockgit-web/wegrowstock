package com.invsys.integration.alerts;

import com.invsys.api.AlertPreferencesController;
import com.invsys.media.MediaUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SlackWebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SlackWebhookDispatcher.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper objectMapper;
    private final MediaUrlValidator mediaUrlValidator;

    public SlackWebhookDispatcher(ObjectMapper objectMapper, MediaUrlValidator mediaUrlValidator) {
        this.objectMapper = objectMapper;
        this.mediaUrlValidator = mediaUrlValidator;
    }

    public boolean dispatch(String webhookUrl, String title, String body) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return false;
        }
        try {
            mediaUrlValidator.assertAllowedHttpsHost(
                    webhookUrl, AlertPreferencesController.SLACK_HOSTS, "INVALID_SLACK_URL");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("text", "*" + title + "*\n" + body);
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("type", "section");
            block.put("text", Map.of("type", "mrkdwn", "text", "*" + title + "*\n" + body));
            payload.put("blocks", java.util.List.of(block));

            String json = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl.trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            log.warn("Slack webhook returned HTTP {}: {}", response.statusCode(), response.body());
            return false;
        } catch (Exception ex) {
            log.warn("Slack webhook dispatch failed: {}", ex.getMessage());
            return false;
        }
    }
}
