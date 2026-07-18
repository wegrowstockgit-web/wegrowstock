package com.invsys.common;

import com.invsys.common.exception.BusinessValidationException;
import com.invsys.common.exception.SystemFailureException;
import com.invsys.service.OfflineSyncConflictService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    public GlobalExceptionHandler(OfflineSyncConflictService offlineSyncConflictService) {
        this.offlineSyncConflictService = offlineSyncConflictService;
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
    public ProblemDetail handleSystem(SystemFailureException ex) {
        log.error("System failure code={} message={}", ex.getCode(), ex.getMessage(), ex);
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
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        pd.setTitle("FORBIDDEN");
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
        log.warn("Illegal state: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
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

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SYSTEM_DETAIL);
        pd.setTitle("INTERNAL_ERROR");
        pd.setType(URI.create("about:blank"));
        return pd;
    }

    private ResponseEntity<Map<String, Object>> parkOfflineConflict(
            HttpServletRequest request, String errorCode, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", request.getMethod());
        payload.put("url", request.getRequestURI());
        payload.put("query", request.getQueryString());
        payload.put("idempotencyKey", request.getHeader("Idempotency-Key"));
        payload.put("errorCode", errorCode);
        payload.put("body", request.getAttribute("offlineReplayBody"));
        var saved = offlineSyncConflictService.sink(payload, errorCode + ": " + message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conflictId", saved.getId());
        body.put("status", "PENDING");
        body.put("accepted", true);
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
