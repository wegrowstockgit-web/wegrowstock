package com.invsys.core.common.exception;

import org.springframework.http.HttpStatus;

public class AccountingProviderException extends BusinessValidationException {

    public AccountingProviderException(String code, String detail) {
        super(code, HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}