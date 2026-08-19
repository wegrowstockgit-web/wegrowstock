package com.invsys.core.common.exception;

import org.springframework.http.HttpStatus;

public class SystemRoleLockedException extends BusinessValidationException {

    public static final String DETAIL = "System roles cannot be modified.";

    public SystemRoleLockedException() {
        super("SYSTEM_ROLE_LOCKED", HttpStatus.UNPROCESSABLE_ENTITY, DETAIL);
    }
}
