package com.invsys.core.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetTerminalPinRequest(
        @NotBlank
        @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits")
        String pin
) {
}
