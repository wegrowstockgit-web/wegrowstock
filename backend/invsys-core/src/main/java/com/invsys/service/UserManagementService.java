package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.CustomerUserMapping;
import com.invsys.domain.Invitation;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.Role;
import com.invsys.domain.SupplierUserMapping;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.domain.UserWarehouse;
import com.invsys.observability.Auditable;
import com.invsys.modules.sales.repository.CustomerUserMappingRepository;
import com.invsys.repository.InvitationRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.RefreshTokenRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.modules.purchasing.repository.SupplierUserMappingRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.repository.UserWarehouseRepository;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.invsys.modules.sales.domain.Customer;

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
    private final InvitationEmailService invitationEmailService;

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
                                 AuditService auditService,
                                 InvitationEmailService invitationEmailService) {
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
        this.invitationEmailService = invitationEmailService;
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
                    List<String> roleCodes = invitationRoleCodes(inv);
                    String roleCode = roleCodes.isEmpty() ? "UNKNOWN" : roleCodes.getFirst();
                    return new PendingInvitation(
                            inv.getId(),
                            inv.getEmail(),
                            roleCode,
                            roleCodes,
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
        return invite(email, List.of(roleCode), customerId, null);
    }

    @Transactional
    @Auditable(action = "INVITE_USER", entityType = "INVITATION")
    public InviteResult invite(String email, String roleCode, UUID customerId, UUID supplierId) {
        return invite(email, List.of(roleCode), customerId, supplierId);
    }

    @Transactional
    @Auditable(action = "INVITE_USER", entityType = "INVITATION")
    public InviteResult invite(String email, List<String> roleCodes, UUID customerId, UUID supplierId) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Email is required");
        }
        List<String> codes = normalizeRoleCodes(roleCodes);
        if (codes.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "At least one role is required");
        }
        List<Role> roles = resolveAssignableRoles(tenantId, codes);
        if (codes.contains("B2B_CUSTOMER") && customerId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_REQUIRED", "Customer is required for portal invites");
        }
        if (codes.contains("SUPPLIER") && supplierId == null) {
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
        invitation.setRoleId(roles.getFirst().getId());
        invitation.setAdditionalRoles(codes.size() > 1 ? String.join(",", codes.subList(1, codes.size())) : null);
        invitation.setCustomerId(customerId);
        invitation.setSupplierId(supplierId);
        invitation.setTokenHash(hash(token));
        invitation.setInvitedBy(TenantContext.getUserId().orElseThrow());
        invitation.setExpiresAt(Instant.now().plusSeconds(7 * 86400));
        invitationRepository.save(invitation);

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("email", normalizedEmail);
        diff.put("role", codes.getFirst());
        diff.put("roles", codes);
        diff.put("summary", "Invitation created for " + normalizedEmail + " as " + String.join(",", codes));
        auditService.record("USER_INVITE", "INVITATION", invitation.getId(), diff);

        String inviteUrl = invitationEmailService.inviteUrl(token);
        invitationEmailService.sendInvitation(normalizedEmail, inviteUrl);
        System.out.println("[DEV] Invitation link token for " + normalizedEmail + ": " + token);
        return new InviteResult(invitation, token);
    }

    /**
     * Remints invitation token, extends {@code expires_at} by 7 days, and dispatches HTML reminder email.
     */
    @Transactional
    @Auditable(action = "RESEND_INVITATION", entityType = "INVITATION")
    public ResendInvitationResult resendInvitation(UUID invitationId) {
        UUID tenantId = TenantContext.requireTenantId();
        Invitation invitation = invitationRepository
                .findByTenantIdAndIdAndAcceptedAtIsNull(tenantId, invitationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invitation not found"));
        if (invitation.getAcceptedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITE_ACCEPTED", "Invitation already accepted");
        }

        Instant previousExpires = invitation.getExpiresAt();
        Instant extendedExpires = Instant.now().plusSeconds(7 * 86400);
        String rawToken = UUID.randomUUID().toString();
        invitation.setTokenHash(hash(rawToken));
        invitation.setExpiresAt(extendedExpires);
        invitationRepository.save(invitation);

        String roleCode = roleRepository.findById(invitation.getRoleId())
                .map(Role::getCode)
                .orElse("UNKNOWN");
        UUID actorId = TenantContext.getUserId().orElse(null);
        List<UUID> warehouseScope = actorId != null
                ? warehouseIdsForUser(actorId)
                : List.of();

        String inviteUrl = invitationEmailService.inviteUrl(rawToken);
        boolean dispatched = invitationEmailService.sendInvitation(invitation.getEmail(), inviteUrl);

        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("extendedExpiresAt", extendedExpires.toString());
        diff.put("previousExpiresAt", previousExpires != null ? previousExpires.toString() : null);
        diff.put("targetEmail", invitation.getEmail());
        diff.put("role", roleCode);
        diff.put("warehouseIds", warehouseScope.stream().map(UUID::toString).toList());
        diff.put("emailDispatched", dispatched);
        diff.put("summary", "Invitation reminder resent to " + invitation.getEmail());
        auditService.record("RESEND_INVITATION", "INVITATION", invitation.getId(), diff);

        return new ResendInvitationResult(
                invitation.getId(),
                invitation.getEmail(),
                roleCode,
                extendedExpires,
                inviteUrl,
                dispatched,
                warehouseScope);
    }

    public record PendingInvitation(
            UUID id,
            String email,
            String role,
            List<String> roles,
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

    public record ResendInvitationResult(
            UUID invitationId,
            String email,
            String role,
            Instant expiresAt,
            String inviteUrl,
            boolean emailDispatched,
            List<UUID> warehouseIds
    ) {
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

        List<UUID> roleIds = new ArrayList<>();
        roleIds.add(invitation.roleId());
        for (String extra : splitRoleCodes(invitation.additionalRoles())) {
            roleRepository.findByTenantIdAndCode(invitation.tenantId(), extra)
                    .map(Role::getId)
                    .ifPresent(roleIds::add);
        }
        Set<UUID> assigned = new LinkedHashSet<>(roleIds);
        for (UUID roleId : assigned) {
            UserRole userRole = new UserRole();
            userRole.setTenantId(invitation.tenantId());
            userRole.setUserId(user.getId());
            userRole.setRoleId(roleId);
            userRoleRepository.save(userRole);
        }

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
    @Auditable(action = "UPDATE_USER", entityType = "USER")
    public List<String> replaceRoles(UUID userId, List<String> roleCodes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (!user.getTenantId().equals(TenantContext.requireTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }
        List<String> before = userRoleRepository.findRoleCodesByUserId(userId);
        applyRolesChange(userId, roleCodes);
        List<String> after = userRoleRepository.findRoleCodesByUserId(userId);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("field", "roles");
        diff.put("from", String.join(",", before));
        diff.put("to", String.join(",", after));
        diff.put("summary", "Roles replaced: " + String.join(",", after));
        auditService.record("USER_ORG_UPDATE", "USER", user.getId(), diff);
        return after;
    }

    @Transactional
    @Auditable(action = "UPDATE_USER", entityType = "USER")
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

    /**
     * Appends a role to the user's role set without removing existing roles (multi-role RBAC).
     */
    @Transactional
    @Auditable(action = "UPDATE_USER", entityType = "USER")
    public List<String> addRole(UUID userId, String roleCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (!user.getTenantId().equals(TenantContext.requireTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }
        String code = roleCode.trim().toUpperCase(Locale.ROOT);
        List<String> before = userRoleRepository.findRoleCodesByUserId(userId);
        if (before.contains(code)) {
            return before;
        }
        Role role = roleRepository.findByTenantIdAndCode(user.getTenantId(), code)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Unknown role: " + code));
        UserRole userRole = new UserRole();
        userRole.setTenantId(user.getTenantId());
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleRepository.save(userRole);
        List<String> after = userRoleRepository.findRoleCodesByUserId(userId);
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("field", "roles");
        diff.put("from", String.join(",", before));
        diff.put("to", String.join(",", after));
        diff.put("summary", "Added role " + code);
        auditService.record("USER_ORG_UPDATE", "USER", user.getId(), diff);
        return after;
    }

    @Transactional
    @Auditable(action = "UPDATE_USER", entityType = "USER")
    public OrgScopeResult updateOrgScope(UUID userId, OrgScopeUpdate update) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (!user.getTenantId().equals(TenantContext.requireTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }

        List<Map<String, Object>> changes = new ArrayList<>();
        List<String> beforeRoles = userRoleRepository.findRoleCodesByUserId(userId);
        List<UUID> beforeWarehouses = warehouseIdsForUser(userId);

        List<String> nextRoles = update.roles() != null
                ? normalizeRoleCodes(update.roles())
                : (update.role() != null && !update.role().isBlank()
                        ? List.of(update.role().trim().toUpperCase(Locale.ROOT))
                        : null);
        if (nextRoles != null) {
            if (nextRoles.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "At least one role is required");
            }
            if (!sameRoleSet(beforeRoles, nextRoles)) {
                applyRolesChange(userId, nextRoles);
                changes.add(change("roles", String.join(",", beforeRoles), String.join(",", nextRoles)));
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
    @Auditable(action = "DEACTIVATE_USER", entityType = "USER")
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
        applyRolesChange(userId, List.of(roleCode));
    }

    private void applyRolesChange(UUID userId, List<String> roleCodes) {
        List<String> codes = normalizeRoleCodes(roleCodes);
        if (codes.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "At least one role is required");
        }
        UUID tenantId = TenantContext.requireTenantId();
        List<Role> roles = resolveAssignableRoles(tenantId, codes);
        List<UserRole> existing = userRoleRepository.findByUserId(userId);
        boolean currentlyOwner = existing.stream().anyMatch(ur -> {
            Role r = roleRepository.findById(ur.getRoleId()).orElseThrow();
            return "OWNER".equals(r.getCode());
        });
        if (currentlyOwner && !codes.contains("OWNER")) {
            long ownerCount = roleRepository.findByTenantId(tenantId).stream()
                    .filter(r -> "OWNER".equals(r.getCode()))
                    .map(Role::getId)
                    .mapToLong(userRoleRepository::countByRoleId)
                    .sum();
            if (ownerCount <= 1) {
                throw new ApiException(HttpStatus.CONFLICT, "LAST_OWNER", "Cannot demote the last owner");
            }
        }
        userRoleRepository.deleteByUserId(userId);
        // Flush so re-inserting a previously held role does not hit the unique constraint
        // before the deletes are visible to PostgreSQL (multi-role → single-role restore).
        userRoleRepository.flush();
        for (Role role : roles) {
            UserRole userRole = new UserRole();
            userRole.setTenantId(tenantId);
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            userRoleRepository.save(userRole);
        }
    }

    public static List<String> normalizeRoleCodes(Collection<String> raw) {
        if (raw == null) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            codes.add(value.trim().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(codes);
    }

    static List<String> splitRoleCodes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return normalizeRoleCodes(List.of(csv.split(",")));
    }

    private List<String> invitationRoleCodes(Invitation invitation) {
        List<String> codes = new ArrayList<>();
        roleRepository.findById(invitation.getRoleId())
                .map(Role::getCode)
                .ifPresent(codes::add);
        codes.addAll(splitRoleCodes(invitation.getAdditionalRoles()));
        return normalizeRoleCodes(codes);
    }

    private List<Role> resolveAssignableRoles(UUID tenantId, List<String> codes) {
        List<Role> roles = new ArrayList<>();
        for (String code : codes) {
            Role role = roleRepository.findByTenantIdAndCode(tenantId, code)
                    .orElseGet(() -> {
                        if (!"B2B_CUSTOMER".equals(code) && !"SUPPLIER".equals(code)) {
                            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Invalid role: " + code);
                        }
                        Role created = new Role();
                        created.setTenantId(tenantId);
                        created.setCode(code);
                        created.setSystemRole(true);
                        created.setNetworkAccessLevel(
                                com.invsys.domain.NetworkAccessLevel.defaultForRole(code));
                        return roleRepository.save(created);
                    });
            roles.add(role);
        }
        return roles;
    }

    private static boolean sameRoleSet(List<String> before, List<String> after) {
        return new LinkedHashSet<>(before).equals(new LinkedHashSet<>(after));
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
            List<String> roles,
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
