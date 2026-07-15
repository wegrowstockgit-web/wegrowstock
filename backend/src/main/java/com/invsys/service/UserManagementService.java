package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.CustomerUserMapping;
import com.invsys.domain.Invitation;
import com.invsys.domain.Role;
import com.invsys.domain.SupplierUserMapping;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.repository.CustomerUserMappingRepository;
import com.invsys.repository.InvitationRepository;
import com.invsys.repository.RefreshTokenRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.SupplierUserMappingRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final InvitationRepository invitationRepository;
    private final CustomerUserMappingRepository customerUserMappingRepository;
    private final SupplierUserMappingRepository supplierUserMappingRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository userRepository,
                                 UserRoleRepository userRoleRepository,
                                 RoleRepository roleRepository,
                                 InvitationRepository invitationRepository,
                                 CustomerUserMappingRepository customerUserMappingRepository,
                                 SupplierUserMappingRepository supplierUserMappingRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.invitationRepository = invitationRepository;
        this.customerUserMappingRepository = customerUserMappingRepository;
        this.supplierUserMappingRepository = supplierUserMappingRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> listUsers() {
        return userRepository.findByTenantIdOrderByEmailAsc(TenantContext.requireTenantId());
    }

    @Transactional
    public Invitation invite(String email, String roleCode, UUID customerId) {
        return invite(email, roleCode, customerId, null);
    }

    @Transactional
    public Invitation invite(String email, String roleCode, UUID customerId, UUID supplierId) {
        Role role = roleRepository.findByTenantIdAndCode(TenantContext.requireTenantId(), roleCode)
                .orElseGet(() -> {
                    if (!"B2B_CUSTOMER".equals(roleCode) && !"SUPPLIER".equals(roleCode)) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Invalid role");
                    }
                    Role created = new Role();
                    created.setTenantId(TenantContext.requireTenantId());
                    created.setCode(roleCode);
                    return roleRepository.save(created);
                });
        if ("B2B_CUSTOMER".equals(roleCode) && customerId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_REQUIRED", "Customer is required for portal invites");
        }
        if ("SUPPLIER".equals(roleCode) && supplierId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_REQUIRED", "Supplier is required for vendor portal invites");
        }
        if (userRepository.existsByTenantIdAndEmail(TenantContext.requireTenantId(), email.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "User already exists");
        }
        String token = UUID.randomUUID().toString();
        Invitation invitation = new Invitation();
        invitation.setTenantId(TenantContext.requireTenantId());
        invitation.setEmail(email.toLowerCase(Locale.ROOT));
        invitation.setRoleId(role.getId());
        invitation.setCustomerId(customerId);
        invitation.setSupplierId(supplierId);
        invitation.setTokenHash(hash(token));
        invitation.setInvitedBy(TenantContext.getUserId().orElseThrow());
        invitation.setExpiresAt(Instant.now().plusSeconds(7 * 86400));
        invitationRepository.save(invitation);
        System.out.println("[DEV] Invitation link token for " + email + ": " + token);
        return invitation;
    }

    @Transactional
    public Invitation invite(String email, String roleCode) {
        return invite(email, roleCode, null, null);
    }

    @Transactional
    public User acceptInvitation(String token, String displayName, String password) {
        Invitation invitation = invitationRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "Invalid invitation"));
        if (invitation.getAcceptedAt() != null || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "Invitation expired or used");
        }
        TenantContext.setTenantId(invitation.getTenantId());
        User user = new User();
        user.setTenantId(invitation.getTenantId());
        user.setEmail(invitation.getEmail());
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus("ACTIVE");
        userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setTenantId(invitation.getTenantId());
        userRole.setUserId(user.getId());
        userRole.setRoleId(invitation.getRoleId());
        userRoleRepository.save(userRole);

        Role role = roleRepository.findById(invitation.getRoleId()).orElseThrow();
        if ("B2B_CUSTOMER".equals(role.getCode()) && invitation.getCustomerId() != null) {
            CustomerUserMapping mapping = new CustomerUserMapping();
            mapping.setTenantId(invitation.getTenantId());
            mapping.setCustomerId(invitation.getCustomerId());
            mapping.setUserId(user.getId());
            customerUserMappingRepository.save(mapping);
        }
        if ("SUPPLIER".equals(role.getCode()) && invitation.getSupplierId() != null) {
            SupplierUserMapping mapping = new SupplierUserMapping();
            mapping.setTenantId(invitation.getTenantId());
            mapping.setSupplierId(invitation.getSupplierId());
            mapping.setUserId(user.getId());
            supplierUserMappingRepository.save(mapping);
        }

        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);
        return user;
    }

    @Transactional
    public void changeRole(UUID userId, String roleCode) {
        Role role = roleRepository.findByTenantIdAndCode(TenantContext.requireTenantId(), roleCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Invalid role"));
        if ("OWNER".equals(roleCode)) {
            // allowed
        }
        List<UserRole> existing = userRoleRepository.findByUserId(userId);
        if (existing.stream().anyMatch(ur -> {
            Role r = roleRepository.findById(ur.getRoleId()).orElseThrow();
            return "OWNER".equals(r.getCode());
        }) && !"OWNER".equals(roleCode)) {
            long ownerCount = roleRepository.findByTenantId(TenantContext.requireTenantId()).stream()
                    .filter(r -> "OWNER".equals(r.getCode()))
                    .map(Role::getId)
                    .mapToLong(ownerRoleId -> userRoleRepository.countByRoleId(ownerRoleId))
                    .sum();
            if (ownerCount <= 1) {
                throw new ApiException(HttpStatus.CONFLICT, "LAST_OWNER", "Cannot demote the last owner");
            }
        }
        userRoleRepository.deleteByUserId(userId);
        UserRole userRole = new UserRole();
        userRole.setTenantId(TenantContext.requireTenantId());
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleRepository.save(userRole);
    }

    @Transactional
    public void deactivate(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        user.setStatus("INACTIVE");
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
