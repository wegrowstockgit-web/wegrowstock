package com.invsys.pos;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.PermissionKeys;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.User;
import com.invsys.pos.dto.PosManagerOverrideResponse;
import com.invsys.pos.dto.PosManagerOverrideResponse.ManagerPin;
import com.invsys.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Morning-sync payload of supervisor PIN hashes ({@code pos.supervise}) for offline voids.
 */
@Service
public class PosManagerOverrideService {

    private final UserRepository userRepository;
    private final AuthService authService;

    public PosManagerOverrideService(UserRepository userRepository, AuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public PosManagerOverrideResponse currentManagers() {
        UUID tenantId = authService.currentUser().tenantId();
        TenantContext.setTenantId(tenantId);
        List<User> managers = userRepository.findActiveUsersWithPinAndPermission(
                tenantId, PermissionKeys.POS_SUPERVISE);
        List<ManagerPin> pins = managers.stream()
                .filter(user -> user.getTerminalPinHash() != null && !user.getTerminalPinHash().isBlank())
                .map(user -> new ManagerPin(user.getId(), user.getTerminalPinHash()))
                .toList();
        return new PosManagerOverrideResponse(tenantId, pins);
    }
}
