package com.invsys.api;

import com.invsys.auth.AuthService;
import com.invsys.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@PreAuthorize("isAuthenticated()")
public class ProfileController {

    private final AuthService authService;

    public ProfileController(AuthService authService) {
        this.authService = authService;
    }

    @PutMapping("/avatar")
    public AvatarResponse updateAvatar(@Valid @RequestBody AvatarRequest request) {
        User user = authService.updateMyAvatar(request.avatarUrl());
        return new AvatarResponse(user.getId(), user.getAvatarUrl());
    }

    public record AvatarRequest(
            @NotBlank @Size(max = 1024) String avatarUrl
    ) {
    }

    public record AvatarResponse(UUID userId, String avatarUrl) {
    }
}
