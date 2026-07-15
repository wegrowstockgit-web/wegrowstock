package com.invsys.api;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SetTerminalPinRequest;
import com.invsys.domain.Invitation;
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
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getStatus(),
                        userRoleRepository.findRoleCodesByUserId(user.getId())))
                .toList();
    }

    @PostMapping("/invitations")
    public Invitation invite(@Valid @RequestBody InviteRequest request) {
        return userManagementService.invite(
                request.email(), request.role(), request.customerId(), request.supplierId());
    }

    @PatchMapping("/{id}/role")
    public void changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        userManagementService.changeRole(id, request.role());
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

    public record UserResponse(
            UUID id,
            String email,
            String displayName,
            String status,
            List<String> roles
    ) {
    }
}
