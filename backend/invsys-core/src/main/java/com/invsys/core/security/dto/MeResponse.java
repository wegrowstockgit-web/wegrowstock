package com.invsys.core.security.dto;

import com.invsys.domain.subscription.AppModule;

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
        String uiDensityPreference,
        /** Union of granted permission keys across all assigned roles. */
        List<String> grantedPermissions,
        /** Always false — platform admins live in {@code platform_admins}, not tenant users. */
        boolean isSuperAdmin,
        /** Commercial modules enabled for this tenant. */
        List<AppModule> enabledModules
) {
}
