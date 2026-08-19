package com.invsys.integration.accounting;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class DefaultAccountingHttpTransport implements AccountingHttpTransport {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public Response get(String url, Map<String, String> headers) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), headers);
    }

    @Override
    public Response post(String url, Map<String, String> headers, String jsonBody) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody)), headers);
    }

    private Response send(HttpRequest.Builder builder, Map<String, String> headers) {
        try {
            if (headers != null) {
                headers.forEach(builder::header);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Accounting HTTP call interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Accounting HTTP call failed: " + ex.getMessage(), ex);
        }
    }
}
