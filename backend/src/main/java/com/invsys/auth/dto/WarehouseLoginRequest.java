package com.invsys.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WarehouseLoginRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits") String pin,
        String deviceId
) {
}
