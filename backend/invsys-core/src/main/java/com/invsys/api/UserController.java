package com.invsys.api;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SetTerminalPinRequest;
import com.invsys.domain.Invitation;
import com.invsys.domain.User;
import com.invsys.repository.UserRoleRepository;
import com.invsys.service.TerminalBiometricService;
import com.invsys.service.UserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class UserController {

    private final UserManagementService userManagementService;
    private final UserRoleRepository userRoleRepository;
    private final AuthService authService;
    private final TerminalBiometricService terminalBiometricService;

    public UserController(UserManagementService userManagementService,
                          UserRoleRepository userRoleRepository,
                          AuthService authService,
                          TerminalBiometricService terminalBiometricService) {
        this.userManagementService = userManagementService;
        this.userRoleRepository = userRoleRepository;
        this.authService = authService;
        this.terminalBiometricService = terminalBiometricService;
    }

    @GetMapping
    public List<UserResponse> listUsers() {
        return userManagementService.listUsers().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/invitations")
    public List<PendingInvitationResponse> listPendingInvitations() {
        return userManagementService.listPendingInvitations().stream()
                .map(p -> new PendingInvitationResponse(
                        p.id(),
                        p.email(),
                        p.role(),
                        p.expiresAt(),
                        p.invitedBy(),
                        p.customerId(),
                        p.supplierId()))
                .toList();
    }

    @PostMapping("/invitations")
    public InviteResponse invite(@Valid @RequestBody InviteRequest request) {
        UserManagementService.InviteResult result = userManagementService.invite(
                request.email(), request.role(), request.customerId(), request.supplierId());
        Invitation invitation = result.invitation();
        return new InviteResponse(
                invitation.getId(),
                invitation.getEmail(),
                request.role(),
                invitation.getTokenHash(),
                result.rawToken(),
                invitation.getExpiresAt());
    }

    @PatchMapping("/{id}/role")
    public void changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        userManagementService.changeRole(id, request.role());
    }

    /**
     * Append a role onto the user (multi-role). Does not remove existing roles.
     */
    @PostMapping("/{id}/roles")
    public Map<String, Object> addRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        List<String> roles = userManagementService.addRole(id, request.role());
        return Map.of("userId", id, "roles", roles);
    }

    /**
     * Admin-only organizational scope: role, warehouse LBAC, timezone, locale, department, shift.
     */
    @PatchMapping("/{id}/org-scope")
    public UserResponse updateOrgScope(@PathVariable UUID id,
                                       @Valid @RequestBody OrgScopeRequest request) {
        boolean clearAssigned = Boolean.TRUE.equals(request.clearAssignedWarehouse());
        UserManagementService.OrgScopeResult result = userManagementService.updateOrgScope(
                id,
                new UserManagementService.OrgScopeUpdate(
                        request.role(),
                        request.corporateDepartment() != null
                                ? request.corporateDepartment()
                                : request.department(),
                        request.timezonePreference(),
                        request.localeLanguage(),
                        request.shiftScheduleType() != null
                                ? request.shiftScheduleType()
                                : request.shiftSchedule(),
                        clearAssigned ? null : request.assignedWarehouseId(),
                        clearAssigned,
                        request.warehouseIds()));
        return toResponse(result.user(), result.roles(), result.warehouseIds());
    }

    @PostMapping("/{id}/deactivate")
    public void deactivate(@PathVariable UUID id) {
        userManagementService.deactivate(id);
    }

    @PostMapping("/{id}/terminal-pin")
    public ResponseEntity<Void> setTerminalPin(@PathVariable UUID id,
                                               @Valid @RequestBody SetTerminalPinRequest request) {
        authService.setTerminalPin(id, request.pin());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/terminal-passkey")
    public Map<String, String> registerPasskey(@PathVariable UUID id,
                                               @RequestBody(required = false) PasskeyLabelRequest request) {
        return terminalBiometricService.registerCredential(
                id, request != null ? request.label() : null);
    }

    private UserResponse toResponse(User user) {
        return toResponse(
                user,
                userRoleRepository.findRoleCodesByUserId(user.getId()),
                userManagementService.warehouseIdsForUser(user.getId()));
    }

    private UserResponse toResponse(User user, List<String> roles, List<UUID> warehouseIds) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                roles,
                user.getCorporateDepartment(),
                user.getDepartment(),
                user.getTimezonePreference(),
                user.getLocaleLanguage(),
                user.getAssignedWarehouseId(),
                user.isMfaEnabled(),
                user.getShiftScheduleType(),
                user.getShiftSchedule(),
                warehouseIds);
    }

    public record InviteRequest(
            @NotBlank @Email String email,
            @NotBlank String role,
            UUID customerId,
            UUID supplierId
    ) {
    }

    public record ChangeRoleRequest(@NotBlank String role) {
    }

    public record PasskeyLabelRequest(String label) {
    }

    public record OrgScopeRequest(
            String role,
            String corporateDepartment,
            String department,
            String timezonePreference,
            String localeLanguage,
            String shiftScheduleType,
            String shiftSchedule,
            UUID assignedWarehouseId,
            Boolean clearAssignedWarehouse,
            List<UUID> warehouseIds
    ) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String displayName,
            String status,
            List<String> roles,
            String corporateDepartment,
            String department,
            String timezonePreference,
            String localeLanguage,
            UUID assignedWarehouseId,
            boolean mfaEnabled,
            String shiftScheduleType,
            String shiftSchedule,
            List<UUID> warehouseIds
    ) {
    }

    public record InviteResponse(
            UUID id,
            String email,
            String role,
            String tokenHash,
            String token,
            Instant expiresAt
    ) {
    }

    public record PendingInvitationResponse(
            UUID id,
            String email,
            String role,
            Instant expiresAt,
            UUID invitedBy,
            UUID customerId,
            UUID supplierId
    ) {
    }
}
