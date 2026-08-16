package com.invsys.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WholesaleApplyRequest(
        @NotBlank @Size(max = 255) String companyName,
        @NotBlank @Size(max = 64) String taxId,
        @NotBlank @Size(max = 255) String contactName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 64) String phone,
        @Size(max = 80) String tenantSlug
) {
}
