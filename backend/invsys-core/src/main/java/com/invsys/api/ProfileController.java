package com.invsys.api;

import com.invsys.core.security.AuthService;
import com.invsys.domain.MediaObject;
import com.invsys.domain.User;
import com.invsys.domain.WorkstationSettings;
import com.invsys.media.MediaAttachmentService;
import com.invsys.media.MediaUploadService;
import com.invsys.service.WorkstationSettingsService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final AuthService authService;
    private final MediaUploadService mediaUploadService;
    private final MediaAttachmentService mediaAttachmentService;
    private final WorkstationSettingsService workstationSettingsService;

    public ProfileController(AuthService authService,
                             MediaUploadService mediaUploadService,
                             MediaAttachmentService mediaAttachmentService,
                             WorkstationSettingsService workstationSettingsService) {
        this.authService = authService;
        this.mediaUploadService = mediaUploadService;
        this.mediaAttachmentService = mediaAttachmentService;
        this.workstationSettingsService = workstationSettingsService;
    }

    @GetMapping("/workstation")
    public WorkstationResponse getWorkstation() {
        return WorkstationResponse.from(workstationSettingsService.getOrDefaultForCurrentUser());
    }

    @PatchMapping("/workstation")
    public WorkstationResponse patchWorkstation(@Valid @RequestBody WorkstationPatchRequest request) {
        WorkstationSettings updated = workstationSettingsService.updateCurrentUser(
                request.printMode(), request.zplPrinterName(), request.labelFormat());
        return WorkstationResponse.from(updated);
    }

    /**
     * Self-service personal profile — contact, password prefs, MFA flag, UI density.
     * Organizational fields are rejected here (admin org-scope API only).
     */
    @PatchMapping("/profile")
    public ProfileResponse updateProfile(@Valid @RequestBody ProfilePatchRequest request) {
        User user = authService.updateMyProfile(
                request.displayName(),
                request.phone(),
                request.addressLine1(),
                request.addressLine2(),
                request.addressCity(),
                request.addressRegion(),
                request.addressPostalCode(),
                request.addressCountry(),
                request.mfaEnabled(),
                request.uiDensityPreference(),
                firstNonBlank(request.localeLanguage(), request.preferredLanguage()));
        return ProfileResponse.from(user);
    }

    private static String firstNonBlank(String primary, String alias) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        return primary != null ? primary : alias;
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changeMyPassword(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/avatar")
    public AvatarResponse updateAvatar(@Valid @RequestBody AvatarRequest request) {
        User user = authService.updateMyAvatar(request.avatarUrl());
        return new AvatarResponse(user.getId(), user.getAvatarUrl());
    }

    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarResponse uploadAvatar(@RequestPart("file") MultipartFile file) {
        UUID userId = TenantContext.getUserId()
                .orElseThrow();
        MediaObject media = mediaUploadService.upload(file, MediaUploadService.UploadKind.AVATAR);
        mediaAttachmentService.attach(media.getId(), "USER", userId, "AVATAR", 0);
        User user = authService.updateMyAvatar(mediaUploadService.contentPath(media.getId()));
        return new AvatarResponse(user.getId(), user.getAvatarUrl());
    }

    public record ProfilePatchRequest(
            String displayName,
            String phone,
            String addressLine1,
            String addressLine2,
            String addressCity,
            String addressRegion,
            String addressPostalCode,
            String addressCountry,
            Boolean mfaEnabled,
            String uiDensityPreference,
            String localeLanguage,
            String preferredLanguage
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {
    }

    public record ProfileResponse(
            UUID userId,
            String displayName,
            String phone,
            String addressLine1,
            String addressLine2,
            String addressCity,
            String addressRegion,
            String addressPostalCode,
            String addressCountry,
            boolean mfaEnabled,
            String uiDensityPreference,
            UUID assignedWarehouseId,
            String corporateDepartment,
            String timezonePreference,
            String localeLanguage,
            String shiftScheduleType
    ) {
        static ProfileResponse from(User user) {
            return new ProfileResponse(
                    user.getId(),
                    user.getDisplayName(),
                    user.getPhone(),
                    user.getAddressLine1(),
                    user.getAddressLine2(),
                    user.getAddressCity(),
                    user.getAddressRegion(),
                    user.getAddressPostalCode(),
                    user.getAddressCountry(),
                    user.isMfaEnabled(),
                    user.getUiDensityPreference(),
                    user.getAssignedWarehouseId(),
                    user.getCorporateDepartment(),
                    user.getTimezonePreference(),
                    user.getLocaleLanguage(),
                    user.getShiftScheduleType());
        }
    }

    public record AvatarRequest(
            @NotBlank @Size(max = 1024) String avatarUrl
    ) {
    }

    public record AvatarResponse(UUID userId, String avatarUrl) {
    }

    public record WorkstationPatchRequest(
            String printMode,
            String zplPrinterName,
            String labelFormat
    ) {
    }

    public record WorkstationResponse(
            UUID id,
            String printMode,
            String zplPrinterName,
            String labelFormat
    ) {
        static WorkstationResponse from(WorkstationSettings settings) {
            return new WorkstationResponse(
                    settings.getId(),
                    settings.getPrintMode(),
                    settings.getZplPrinterName(),
                    settings.getLabelFormat());
        }
    }
}
