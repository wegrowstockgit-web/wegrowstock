package com.invsys.api;

import com.invsys.auth.AuthService;
import com.invsys.domain.MediaObject;
import com.invsys.domain.User;
import com.invsys.media.MediaAttachmentService;
import com.invsys.media.MediaUploadService;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public ProfileController(AuthService authService,
                             MediaUploadService mediaUploadService,
                             MediaAttachmentService mediaAttachmentService) {
        this.authService = authService;
        this.mediaUploadService = mediaUploadService;
        this.mediaAttachmentService = mediaAttachmentService;
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

    public record AvatarRequest(
            @NotBlank @Size(max = 1024) String avatarUrl
    ) {
    }

    public record AvatarResponse(UUID userId, String avatarUrl) {
    }
}
