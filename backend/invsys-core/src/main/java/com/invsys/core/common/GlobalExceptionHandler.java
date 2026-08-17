package com.invsys.core.common;

import com.invsys.core.common.exception.BusinessValidationException;
import com.invsys.core.common.exception.SystemFailureException;
import com.invsys.domain.ConflictActionType;
import com.invsys.metrics.WmsMetrics;
import com.invsys.service.OfflineSyncConflictService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RFC 7807 Problem Details router.
 * Business validation → warn + client-safe detail; system failures → error + generic detail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_SYSTEM_DETAIL = "An unexpected system error occurred";

    private final OfflineSyncConflictService offlineSyncConflictService;
    private final WmsMetrics wmsMetrics;

    public GlobalExceptionHandler(OfflineSyncConflictService offlineSyncConflictService, WmsMetrics wmsMetrics) {
        this.offlineSyncConflictService = offlineSyncConflictService;
        this.wmsMetrics = wmsMetrics;
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<?> handleBusiness(BusinessValidationException ex, HttpServletRequest request) {
        log.warn("Business validation failed code={} detail={}", ex.getCode(), ex.getMessage());
        if (isOfflineReplay(request) && isClientError(ex.getStatus())) {
            return parkOfflineConflict(request, ex.getCode(), ex.getMessage());
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setTitle(ex.getCode());
        pd.setType(URI.create("about:blank"));
        pd.setProperty("code", ex.getCode());
        return ResponseEntity.status(ex.getStatus()).body(pd);
    }

    @ExceptionHandler(SystemFailureException.class)
    public ProblemDetail handleSystem(SystemFailureException ex, HttpServletRequest request) {
        log.error("System failure code={} message={}", ex.getCode(), ex.getMessage(), ex);
        wmsMetrics.incrementApiError(endpointTag(request));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SYSTEM_DETAIL);
        pd.setTitle("INTERNAL_ERROR");
        pd.setType(URI.create("about:blank"));
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApi(ApiException ex, HttpServletRequest request) {
        if (isOfflineReplay(request) && isBusinessRuleFailure(ex)) {
            return parkOfflineConflict(request, ex.getCode(), ex.getMessage());
        }
        if (ex.getStatus().is5xxServerError()) {
            log.error("ApiException system-path code={} detail={}", ex.getCode(), ex.getMessage(), ex);
            wmsMetrics.incrementApiError(endpointTag(request));
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SYSTEM_DETAIL);
            pd.setTitle("INTERNAL_ERROR");
            pd.setType(URI.create("about:blank"));
            pd.setProperty("code", ex.getCode());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
        }
        log.warn("ApiException code={} detail={}", ex.getCode(), ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setTitle(ex.getCode());
        pd.setType(URI.create("about:blank"));
        pd.setProperty("code", ex.getCode());
        ex.getProperties().forEach(pd::setProperty);
        return ResponseEntity.status(ex.getStatus()).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Request validation failed: {}", detail);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("VALIDATION_ERROR");
        pd.setType(URI.create("about:blank"));
        pd.setProperty("code", "VALIDATION_ERROR");
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        org.springframework.validation.FieldError::getField,
                        e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "invalid",
                        (a, b) -> a,
                        java.util.LinkedHashMap::new));
        pd.setProperty("errors", fields);
        return pd;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuth(AuthenticationException ex) {
        // SOC 2: never echo raw auth internals / stack fragments to clients
        log.warn("Authentication failed: {}", ex.getClass().getSimpleName());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Authentication required");
        pd.setTitle("UNAUTHORIZED");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccess(AccessDeniedException ex) {
        String detail = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Access denied"
                : ex.getMessage();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
        pd.setTitle("FORBIDDEN");
        pd.setProperty("code", "ACCESS_DENIED");
        return pd;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException ex) {
        log.warn("No resource for {} {}", ex.getHttpMethod(), ex.getResourcePath());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
        pd.setTitle("NOT_FOUND");
        pd.setType(URI.create("about:blank"));
        pd.setProperty("code", "NOT_FOUND");
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegal(IllegalStateException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Illegal state";
        // Controllers that call TenantContext.require* without a bound JWT land here.
        // That is an auth/context failure, not a business conflict.
        if (message.contains("context not set")) {
            log.warn("Missing request context: {}", message);
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Authentication required");
            pd.setTitle("UNAUTHORIZED");
            pd.setType(URI.create("about:blank"));
            pd.setProperty("code", "UNAUTHORIZED");
            return pd;
        }
        log.warn("Illegal state: {}", message);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
        pd.setTitle("CONFLICT");
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String detail = "A record with this identifier already exists";
        if (ex.getMessage() != null && ex.getMessage().contains("invoices_tenant_id_number_key")) {
            detail = "Invoice number already exists — document sequence may be out of sync";
        }
        log.warn("Data integrity violation: {}", detail);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        pd.setTitle("CONFLICT");
        return pd;
    }

    /**
     * SSE idle timeout / client navigation away — expected, not a server fault.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.debug("Async request timed out: {}", ex.toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @ExceptionHandler({
            ClientAbortException.class,
            AsyncRequestNotUsableException.class
    })
    public ResponseEntity<Void> handleClientGone(Exception ex) {
        log.debug("Client disconnected: {}", ex.toString());
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build();
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<Void> handleNotWritable(HttpMessageNotWritableException ex, HttpServletRequest request) {
        if (isBrokenPipe(ex)) {
            log.debug("Response write aborted (client gone): {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).build();
        }
        log.error("Unhandled exception", ex);
        wmsMetrics.incrementApiError(endpointTag(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        if (isBrokenPipe(ex)) {
            log.debug("Client disconnected during response: {}", ex.toString());
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.REQUEST_TIMEOUT, "Client disconnected");
            pd.setTitle("CLIENT_DISCONNECTED");
            return pd;
        }
        log.error("Unhandled exception", ex);
        wmsMetrics.incrementApiError(endpointTag(request));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SYSTEM_DETAIL);
        pd.setTitle("INTERNAL_ERROR");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    private static String endpointTag(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String method = request.getMethod() != null ? request.getMethod() : "";
        String uri = request.getRequestURI() != null ? request.getRequestURI() : "unknown";
        return (method + " " + uri).trim();
    }

    private static boolean isBrokenPipe(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof ClientAbortException || cur instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String message = cur.getMessage();
            if (message != null
                    && (message.contains("Broken pipe")
                    || message.contains("Connection reset")
                    || message.contains("AsyncRequestNotUsableException"))) {
                return true;
            }
            if (cur instanceof IOException && message != null && message.toLowerCase().contains("broken pipe")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private ResponseEntity<Map<String, Object>> parkOfflineConflict(
            HttpServletRequest request, String errorCode, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", request.getMethod());
        payload.put("url", request.getRequestURI());
        payload.put("query", request.getQueryString());
        payload.put("idempotencyKey", request.getHeader("Idempotency-Key"));
        payload.put("errorCode", errorCode);
        Object replayBody = request.getAttribute("offlineReplayBody");
        payload.put("body", replayBody);

        ConflictActionType actionType = null;
        if (replayBody instanceof Map<?, ?> map && map.get("mode") != null) {
            actionType = ConflictActionType.fromScanMode(String.valueOf(map.get("mode")));
        }

        var saved = offlineSyncConflictService.sink(
                payload,
                errorCode + ": " + message,
                actionType,
                TenantContext.getUserId().orElse(null),
                request.getRequestURI());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conflictId", saved.getId());
        body.put("status", "PENDING");
        body.put("accepted", true);
        body.put("actionType", saved.getActionType() != null ? saved.getActionType().name() : null);
        body.put("message", "Offline mutation parked in sync conflict queue");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    private static boolean isOfflineReplay(HttpServletRequest request) {
        String header = request.getHeader("X-Offline-Replay");
        return header != null && ("1".equals(header) || "true".equalsIgnoreCase(header));
    }

    private static boolean isBusinessRuleFailure(ApiException ex) {
        int code = ex.getStatus().value();
        return code == 409 || code == 422;
    }

    private static boolean isClientError(HttpStatus status) {
        return status != null && (status.value() == 409 || status.value() == 422);
    }
}
