package com.invsys.integration.easypost;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Live EasyPost shipment purchase client. Requires {@code EASYPOST_API_KEY} at startup.
 */
@Component
@Profile("prod")
public class LiveEasyPostGateway implements EasyPostGateway, EasyPostClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public LiveEasyPostGateway(
            ObjectMapper objectMapper,
            @Value("${invsys.easypost.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank() || "easypost_mock_key".equals(apiKey)) {
            throw new IllegalStateException(
                    "EASYPOST_API_KEY (invsys.easypost.api-key) must be configured for production profile");
        }
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public LabelResult purchaseLabel(String carrier, BigDecimal weightLb, String reference) {
        try {
            String oz = weightLb == null ? "16" : weightLb.multiply(new BigDecimal("16")).toPlainString();
            String json = """
                    {
                      "shipment": {
                        "reference": %s,
                        "parcel": { "weight": %s },
                        "carrier_accounts": []
                      }
                    }
                    """.formatted(objectMapper.writeValueAsString(reference != null ? reference : "invsys"), oz);

            String auth = Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.easypost.com/v2/shipments"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "EasyPost API error HTTP " + response.statusCode());
            }
            JsonNode body = objectMapper.readTree(response.body());
            String tracking = body.path("tracking_code").asText("EP-" + System.currentTimeMillis());
            String labelUrl = body.path("postage_label").path("label_url").asText("easypost://" + tracking);
            BigDecimal postage = BigDecimal.ZERO;
            if (body.path("selected_rate").path("rate").isTextual()
                    || body.path("selected_rate").path("rate").isNumber()) {
                postage = new BigDecimal(body.path("selected_rate").path("rate").asText("0"));
            }
            return new LabelResult(labelUrl, tracking, postage);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "EasyPost API call failed: " + ex.getMessage(), ex);
        }
    }
}
