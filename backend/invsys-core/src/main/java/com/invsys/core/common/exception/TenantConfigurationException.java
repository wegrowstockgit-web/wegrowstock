package com.invsys.core.common.exception;

import org.springframework.http.HttpStatus;
import com.invsys.domain.Tenant;

public class TenantConfigurationException extends BusinessValidationException {

    public TenantConfigurationException(String message) {
        super("TENANT_CONFIGURATION", HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public TenantConfigurationException() {
        this("Tenant configuration is incomplete or invalid for this operation");
    }
}
