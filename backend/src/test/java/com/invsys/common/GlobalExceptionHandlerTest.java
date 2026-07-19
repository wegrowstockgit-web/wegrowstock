package com.invsys.common;

import com.invsys.common.exception.InsufficientStockException;
import com.invsys.common.exception.IntegrationTimeoutException;
import com.invsys.common.exception.InvalidAllocationException;
import com.invsys.common.exception.StaleStateConcurrencyException;
import com.invsys.common.exception.TenantConfigurationException;
import com.invsys.domain.OfflineSyncConflict;
import com.invsys.service.OfflineSyncConflictService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock OfflineSyncConflictService offlineSyncConflictService;
    @Mock HttpServletRequest request;

    GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(offlineSyncConflictService);
    }

    @Test
    void businessValidationReturnsClientDetailAndStatus() {
        when(request.getHeader("X-Offline-Replay")).thenReturn(null);

        ResponseEntity<?> response = handler.handleBusiness(
                new InsufficientStockException("Only 2 units left"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail pd = (ProblemDetail) response.getBody();
        assertThat(pd).isNotNull();
        assertThat(pd.getDetail()).isEqualTo("Only 2 units left");
        assertThat(pd.getTitle()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(pd.getProperties()).containsEntry("code", "INSUFFICIENT_STOCK");
    }

    @Test
    void tenantConfigurationIsUnprocessable() {
        when(request.getHeader("X-Offline-Replay")).thenReturn(null);

        ResponseEntity<?> response = handler.handleBusiness(
                new TenantConfigurationException("Staging bin missing"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ProblemDetail pd = (ProblemDetail) response.getBody();
        assertThat(pd.getDetail()).contains("Staging bin missing");
    }

    @Test
    void invalidAllocationIsConflict() {
        when(request.getHeader("X-Offline-Replay")).thenReturn(null);

        ResponseEntity<?> response = handler.handleBusiness(
                new InvalidAllocationException("Allocation claimed by another device"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void systemFailureHidesInternalMessage() {
        ProblemDetail pd = handler.handleSystem(
                new IntegrationTimeoutException("Shopify socket timed out after 30s"));

        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).isEqualTo("An unexpected system error occurred");
        assertThat(pd.getDetail()).doesNotContain("Shopify");
        assertThat(pd.getProperties()).containsEntry("code", "INTEGRATION_TIMEOUT");
    }

    @Test
    void staleStateAlsoGeneric() {
        ProblemDetail pd = handler.handleSystem(new StaleStateConcurrencyException("row version 3 vs 7"));
        assertThat(pd.getDetail()).isEqualTo("An unexpected system error occurred");
        assertThat(pd.getProperties()).containsEntry("code", "STALE_STATE_CONCURRENCY");
    }

    @Test
    void genericExceptionDoesNotLeakStackMessage() {
        ProblemDetail pd = handler.handleGeneric(new RuntimeException("secret JDBC password xyz"));
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).isEqualTo("An unexpected system error occurred");
        assertThat(pd.getDetail()).doesNotContain("password");
    }

    @Test
    void missingTenantContextIsUnauthorized() {
        ProblemDetail pd = handler.handleIllegal(new IllegalStateException("Tenant context not set"));
        assertThat(pd.getStatus()).isEqualTo(401);
        assertThat(pd.getTitle()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void asyncTimeoutIsNotInternalError() {
        ResponseEntity<Void> response = handler.handleAsyncTimeout(new AsyncRequestTimeoutException());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void brokenPipeWriteIsNotInternalError() {
        ResponseEntity<Void> response = handler.handleNotWritable(
                new HttpMessageNotWritableException(
                        "Could not write JSON",
                        new IOException("Broken pipe")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUEST_TIMEOUT);
    }

    @Test
    void businessOfflineReplayParksConflict() {
        when(request.getHeader("X-Offline-Replay")).thenReturn("1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/inventory/adjust");
        when(request.getQueryString()).thenReturn(null);
        when(request.getHeader("Idempotency-Key")).thenReturn("k1");
        when(request.getAttribute("offlineReplayBody")).thenReturn(Map.of("delta", -1));

        OfflineSyncConflict saved = new OfflineSyncConflict();
        saved.setId(UUID.randomUUID());
        when(offlineSyncConflictService.sink(any(), anyString())).thenReturn(saved);

        ResponseEntity<?> response = handler.handleBusiness(
                new InsufficientStockException("no stock"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("accepted", true);
        assertThat(body).containsKey("conflictId");
    }
}
