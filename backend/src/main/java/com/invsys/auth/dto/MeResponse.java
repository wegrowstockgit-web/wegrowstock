package com.invsys.auth.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID userId,
        UUID tenantId,
        String email,
        String displayName,
        List<String> roles,
        List<UUID> warehouseIds,
        String avatarUrl,
        /** Org-scope (admin-managed); mirrored as corporateDepartment. */
        String department,
        String corporateDepartment,
        String timezonePreference,
        String localeLanguage,
        UUID assignedWarehouseId,
        boolean mfaEnabled,
        /** Org-scope shift; mirrored as shiftScheduleType. */
        String shiftSchedule,
        String shiftScheduleType,
        String phone,
        String addressLine1,
        String addressLine2,
        String addressCity,
        String addressRegion,
        String addressPostalCode,
        String addressCountry,
        String uiDensityPreference
) {
}
