package com.invsys.common.exception;

import org.springframework.http.HttpStatus;

public class TenantConfigurationException extends BusinessValidationException {

    public TenantConfigurationException(String message) {
        super("TENANT_CONFIGURATION", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public TenantConfigurationException() {
        this("Tenant configuration is incomplete or invalid for this operation");
    }
}
