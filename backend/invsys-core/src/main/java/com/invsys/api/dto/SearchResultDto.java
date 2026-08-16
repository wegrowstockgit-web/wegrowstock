package com.invsys.api.dto;

public record SearchResultDto(
        String category,
        String title,
        String subtitle,
        String route,
        String requiredPermission
) {
}
