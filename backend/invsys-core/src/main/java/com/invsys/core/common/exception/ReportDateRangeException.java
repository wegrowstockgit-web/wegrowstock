package com.invsys.core.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Report window is too wide to aggregate safely (RFC 7807, 400).
 */
public class ReportDateRangeException extends BusinessValidationException {

    public ReportDateRangeException(String message) {
        super("REPORT_RANGE_EXCEEDED", HttpStatus.BAD_REQUEST, message);
    }
}
