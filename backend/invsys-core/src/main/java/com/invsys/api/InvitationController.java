package com.invsys.api;

import com.invsys.domain.User;
import com.invsys.service.UserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final UserManagementService userManagementService;

    public InvitationController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @PostMapping("/accept")
    public User accept(@Valid @RequestBody AcceptInvitationRequest request) {
        return userManagementService.acceptInvitation(request.token(), request.displayName(), request.password());
    }

    public record AcceptInvitationRequest(@NotBlank String token, @NotBlank String displayName, @NotBlank String password) {
    }
}
