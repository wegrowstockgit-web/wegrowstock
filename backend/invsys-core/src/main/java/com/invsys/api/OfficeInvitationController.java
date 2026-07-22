package com.invsys.api;

import com.invsys.service.UserManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/office/invitations")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class OfficeInvitationController {

    private final UserManagementService userManagementService;

    public OfficeInvitationController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @PostMapping("/{id}/resend")
    public ResendResponse resend(@PathVariable UUID id) {
        UserManagementService.ResendInvitationResult result = userManagementService.resendInvitation(id);
        return new ResendResponse(
                result.invitationId(),
                result.email(),
                result.role(),
                result.expiresAt(),
                result.inviteUrl(),
                result.emailDispatched(),
                result.warehouseIds());
    }

    public record ResendResponse(
            UUID id,
            String email,
            String role,
            Instant expiresAt,
            String inviteUrl,
            boolean emailDispatched,
            List<UUID> warehouseIds
    ) {
    }
}
