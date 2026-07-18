package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.CustomerUserMapping;
import com.invsys.domain.Invitation;
import com.invsys.domain.Location;
import com.invsys.domain.Role;
import com.invsys.domain.SupplierUserMapping;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.domain.UserWarehouse;
import com.invsys.repository.CustomerUserMappingRepository;
import com.invsys.repository.InvitationRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.RefreshTokenRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.SupplierUserMappingRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.repository.UserWarehouseRepository;
import com.invsys.tenancy.BootstrapJdbc;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final UserWarehouseRepository userWarehouseRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapJdbc bootstrapJdbc;
    private final AuditService auditService;

    public UserManagementService(UserRepository userRepository,
                                 UserRoleRepository userRoleRepository,
                                 RoleRepository roleRepository,
                                 InvitationRepository invitationRepository,
                                 CustomerUserMappingRepository customerUserMappingRepository,
                                 SupplierUserMappingRepository supplierUserMappingRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 UserWarehouseRepository userWarehouseRepository,
                                 LocationRepository locationRepository,
                                 PasswordEncoder passwordEncoder,
                                 BootstrapJdbc bootstrapJdbc,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.invitationRepository = invitationRepository;
        this.customerUserMappingRepository = customerUserMappingRepository;
        this.supplierUserMappingRepository = supplierUserMappingRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userWarehouseRepository = userWarehouseRepository;
        this.locationRepository = locationRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapJdbc = bootstrapJdbc;
        this.auditService = auditService;
    }

    public List<User> listUsers() {
        return userRepository.findByTenantIdOrderByEmailAsc(TenantContext.requireTenantId());
    }

    public List<PendingInvitation> listPendingInvitations() {
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = Instant.now();
        return invitationRepository.findByTenantIdAndAcceptedAtIsNullOrderByExpiresAtAsc(tenantId).stream()
                .filter(inv -> inv.getExpiresAt() == null || inv.getExpiresAt().isAfter(now))
                .map(inv -> {
                    String roleCode = roleRepository.findById(inv.getRoleId())
                            .map(Role::getCode)
                            .orElse("UNKNOWN");
                    return new PendingInvitation(
                            inv.getId(),
                            inv.getEmail(),
                            roleCode,
                            inv.getExpiresAt(),
                            inv.getInvitedBy(),
                            inv.getCustomerId(),
                            inv.getSupplierId());
                })
                .toList();
    }

    public List<UUID> warehouseIdsForUser(UUID userId) {
        return userWarehouseRepository
                .findByTenantIdAndUserId(TenantContext.requireTenantId(), userId)
                .stream()
                .map(UserWarehouse::getLocationId)
                .toList();
    }

    @Transactional
    public InviteResult invite(String email, String roleCode, UUID customerId) {
        return invite(email, roleCode, customerId, null);
    }

    @Transactional
    public InviteResult invite(String email, String roleCode, UUID customerId, UUID supplierId) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Email is required");
        }
        Role role = roleRepository.findByTenantIdAndCode(tenantId, roleCode)
                .orElseGet(() -> {
                    if (!"B2B_CUSTOMER".equals(roleCode) && !"SUPPLIER".equals(roleCode)) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Invalid role");
                    }
                    Role created = new Role();
                    created.setTenantId(tenantId);
                    created.setCode(roleCode);
                    return roleRepository.save(created);
                });
        if ("B2B_CUSTOMER".equals(roleCode) && customerId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_REQUIRED", "Customer is required for portal invites");
        }
        if ("SUPPLIER".equals(roleCode) && supplierId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_REQUIRED", "Supplier is required for vendor portal invites");
        }
        if (userRepository.existsByTenantIdAndEmail(tenantId, normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "User already exists");
        }
        if (invitationRepository.existsByTenantIdAndEmailIgnoreCaseAndAcceptedAtIsNull(tenantId, normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITE_PENDING",
                    "An open invitation already exists for this email");
        }
        String token = UUID.randomUUID().toString();
        Invitation invitation = new Invitation();
        invitation.setTenantId(tenantId);
        invitation.setEmail(normalizedEmail);
        invitation.setRoleId(role.getId());
        invitation.setCustomerId(customerId);
        invitation.setSupplierId(supplierId);
        invitation.setTokenHash(hash(token));
        invitation.setInvitedBy(TenantContext.getUserId().orElseThrow());
        invitation.setExpiresAt(Instant.now().plusSeconds(7 * 86400));
        invitationRepository.save(invitation);

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("email", normalizedEmail);
        diff.put("role", role.getCode());
        diff.put("summary", "Invitation created for " + normalizedEmail + " as " + role.getCode());
        auditService.record("USER_INVITE", "INVITATION", invitation.getId(), diff);

        System.out.println("[DEV] Invitation link token for " + normalizedEmail + ": " + token);
        return new InviteResult(invitation, token);
    }

    public record PendingInvitation(
            UUID id,
            String email,
            String role,
            Instant expiresAt,
            UUID invitedBy,
            UUID customerId,
            UUID supplierId
    ) {
    }

    @Transactional
    public InviteResult invite(String email, String roleCode) {
        return invite(email, roleCode, null, null);
    }

    public record InviteResult(Invitation invitation, String rawToken) {
    }

    @Transactional
    public User acceptInvitation(String token, String displayName, String password) {
        BootstrapJdbc.InvitationBootstrapRow invitation = bootstrapJdbc
                .findOpenInvitationByTokenHash(hash(token))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "Invalid invitation"));
        if (invitation.acceptedAt() != null || invitation.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "Invitation expired or used");
        }
        TenantContext.setTenantId(invitation.tenantId());
        User user = new User();
        user.setTenantId(invitation.tenantId());
        user.setEmail(invitation.email());
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus("ACTIVE");
        userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setTenantId(invitation.tenantId());
        userRole.setUserId(user.getId());
        userRole.setRoleId(invitation.roleId());
        userRoleRepository.save(userRole);

        Role role = roleRepository.findById(invitation.roleId()).orElseThrow();
        if ("B2B_CUSTOMER".equals(role.getCode()) && invitation.customerId() != null) {
            CustomerUserMapping mapping = new CustomerUserMapping();
            mapping.setTenantId(invitation.tenantId());
            mapping.setCustomerId(invitation.customerId());
            mapping.setUserId(user.getId());
            customerUserMappingRepository.save(mapping);
        }
        if ("SUPPLIER".equals(role.getCode()) && invitation.supplierId() != null) {
            SupplierUserMapping mapping = new SupplierUserMapping();
            mapping.setTenantId(invitation.tenantId());
            mapping.setSupplierId(invitation.supplierId());
            mapping.setUserId(user.getId());
            supplierUserMappingRepository.save(mapping);
        }

        bootstrapJdbc.markInvitationAccepted(invitation.id());
        return user;
    }

    @Transactional
    public void changeRole(UUID userId, String roleCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        String before = String.join(",", userRoleRepository.findRoleCodesByUserId(userId));
        applyRoleChange(userId, roleCode);
        String after = roleCode;
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("field", "role");
        diff.put("from", before);
        diff.put("to", after);
        diff.put("summary", "Role changed from " + before + " to " + after);
        auditService.record("USER_ORG_UPDATE", "USER", user.getId(), diff);
    }

    @Transactional
    public OrgScopeResult updateOrgScope(UUID userId, OrgScopeUpdate update) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (!user.getTenantId().equals(TenantContext.requireTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }

        List<Map<String, Object>> changes = new ArrayList<>();
        List<String> beforeRoles = userRoleRepository.findRoleCodesByUserId(userId);
        List<UUID> beforeWarehouses = warehouseIdsForUser(userId);

        if (update.role() != null && !update.role().isBlank()) {
            String roleCode = update.role().trim().toUpperCase(Locale.ROOT);
            if (!beforeRoles.contains(roleCode) || beforeRoles.size() != 1) {
                applyRoleChange(userId, roleCode);
                changes.add(change("role", String.join(",", beforeRoles), roleCode));
            }
        }

        if (update.corporateDepartment() != null) {
            String before = user.getCorporateDepartment();
            String after = update.corporateDepartment().isBlank() ? null : update.corporateDepartment().trim();
            if (!Objects.equals(before, after)) {
                user.setCorporateDepartment(after);
                changes.add(change("corporateDepartment", before, after));
            }
        }

        if (update.timezonePreference() != null) {
            String before = user.getTimezonePreference();
            String after = update.timezonePreference().isBlank() ? null : update.timezonePreference().trim();
            if (!Objects.equals(before, after)) {
                user.setTimezonePreference(after);
                changes.add(change("timezonePreference", before, after));
            }
        }

        if (update.localeLanguage() != null) {
            String before = user.getLocaleLanguage();
            String after = update.localeLanguage().isBlank() ? null : update.localeLanguage().trim();
            if (!Objects.equals(before, after)) {
                user.setLocaleLanguage(after);
                changes.add(change("localeLanguage", before, after));
            }
        }

        if (update.shiftScheduleType() != null) {
            String before = user.getShiftScheduleType();
            String raw = update.shiftScheduleType().isBlank() ? null : update.shiftScheduleType().trim().toUpperCase();
            if (raw != null && !raw.equals("DAY") && !raw.equals("NIGHT") && !raw.equals("WEEKEND")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                        "shiftScheduleType must be DAY, NIGHT, or WEEKEND");
            }
            if (!Objects.equals(before, raw)) {
                user.setShiftScheduleType(raw);
                changes.add(change("shiftScheduleType", before, raw));
            }
        }

        if (update.assignedWarehouseId() != null) {
            UUID before = user.getAssignedWarehouseId();
            UUID after = update.assignedWarehouseId();
            // Allow clearing via nil UUID sentinel — use Optional pattern: empty UUID string handled by controller.
            if (!Objects.equals(before, after)) {
                if (after != null) {
                    Location loc = locationRepository.findById(after)
                            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WAREHOUSE",
                                    "assignedWarehouseId not found"));
                    if (!"WAREHOUSE".equalsIgnoreCase(loc.getType()) && !"VEHICLE".equalsIgnoreCase(loc.getType())) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WAREHOUSE",
                                "assignedWarehouseId must be a WAREHOUSE or VEHICLE");
                    }
                }
                user.setAssignedWarehouseId(after);
                changes.add(change("assignedWarehouseId",
                        before != null ? before.toString() : null,
                        after != null ? after.toString() : null));
            }
        }

        if (update.clearAssignedWarehouse()) {
            UUID before = user.getAssignedWarehouseId();
            if (before != null) {
                user.setAssignedWarehouseId(null);
                changes.add(change("assignedWarehouseId", before.toString(), null));
            }
        }

        if (update.warehouseIds() != null) {
            List<UUID> after = update.warehouseIds().stream().distinct().toList();
            for (UUID warehouseId : after) {
                Location loc = locationRepository.findById(warehouseId)
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WAREHOUSE",
                                "Warehouse not found: " + warehouseId));
                if (!"WAREHOUSE".equalsIgnoreCase(loc.getType())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WAREHOUSE",
                            "Only WAREHOUSE locations can be assigned for LBAC");
                }
            }
            if (!beforeWarehouses.equals(after)) {
                replaceWarehouses(userId, after);
                changes.add(change("warehouseIds",
                        beforeWarehouses.stream().map(UUID::toString).toList().toString(),
                        after.stream().map(UUID::toString).toList().toString()));
            }
        }

        userRepository.save(user);

        if (!changes.isEmpty()) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("changes", changes);
            diff.put("summary", changes.stream()
                    .map(c -> String.valueOf(c.get("summary")))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Organizational scope updated"));
            auditService.record("USER_ORG_UPDATE", "USER", user.getId(), diff);
        }

        return new OrgScopeResult(
                user,
                userRoleRepository.findRoleCodesByUserId(userId),
                warehouseIdsForUser(userId));
    }

    @Transactional
    public void deactivate(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        user.setStatus("INACTIVE");
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("field", "status");
        diff.put("from", "ACTIVE");
        diff.put("to", "INACTIVE");
        diff.put("summary", "User deactivated");
        auditService.record("USER_ORG_UPDATE", "USER", userId, diff);
    }

    private void applyRoleChange(UUID userId, String roleCode) {
        Role role = roleRepository.findByTenantIdAndCode(TenantContext.requireTenantId(), roleCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Invalid role"));
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

    private void replaceWarehouses(UUID userId, List<UUID> warehouseIds) {
        UUID tenantId = TenantContext.requireTenantId();
        userWarehouseRepository.deleteByTenantIdAndUserId(tenantId, userId);
        for (UUID warehouseId : warehouseIds) {
            UserWarehouse row = new UserWarehouse();
            row.setTenantId(tenantId);
            row.setUserId(userId);
            row.setLocationId(warehouseId);
            userWarehouseRepository.save(row);
        }
    }

    private static Map<String, Object> change(String field, Object from, Object to) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("field", field);
        row.put("from", from);
        row.put("to", to);
        row.put("summary", field + " changed from " + from + " to " + to);
        return row;
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record OrgScopeUpdate(
            String role,
            String corporateDepartment,
            String timezonePreference,
            String localeLanguage,
            String shiftScheduleType,
            UUID assignedWarehouseId,
            boolean clearAssignedWarehouse,
            List<UUID> warehouseIds
    ) {
    }

    public record OrgScopeResult(User user, List<String> roles, List<UUID> warehouseIds) {
    }
}
