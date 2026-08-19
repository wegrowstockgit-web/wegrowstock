package com.invsys.api;

import com.invsys.domain.User;
import com.invsys.service.UserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final UserManagementService userManagementService;

    public InvitationController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @PostMapping("/accept")
    public InviteAcceptResponse accept(@Valid @RequestBody AcceptInvitationRequest request) {
        User user = userManagementService.acceptInvitation(
                request.token(), request.displayName(), request.password());
        return InviteAcceptResponse.from(user);
    }

    public record AcceptInvitationRequest(@NotBlank String token, @NotBlank String displayName, @NotBlank String password) {
    }

    public record InviteAcceptResponse(UUID id, String email, String displayName, String status) {
        static InviteAcceptResponse from(User user) {
            return new InviteAcceptResponse(
                    user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus());
        }
    }
}
