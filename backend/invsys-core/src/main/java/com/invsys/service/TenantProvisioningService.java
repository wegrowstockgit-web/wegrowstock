package com.invsys.service;

import com.invsys.core.security.dto.SignupRequest;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.Role;
import com.invsys.domain.Tenant;
import com.invsys.domain.TenantSettings;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TenantProvisioningService {

    private static final List<String> DEFAULT_ROLES = List.of(
            "OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER",
            "RETAIL_CASHIER", "RETAIL_MANAGER");

    private final TenantRepository tenantRepository;
    private final TenantSettingsRepository tenantSettingsRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;
    private final RolePermissionService rolePermissionService;
    private final TenantSubscriptionService tenantSubscriptionService;

    public TenantProvisioningService(TenantRepository tenantRepository,
                                       TenantSettingsRepository tenantSettingsRepository,
                                       UserRepository userRepository,
                                       RoleRepository roleRepository,
                                       UserRoleRepository userRoleRepository,
                                       LocationRepository locationRepository,
                                       PasswordEncoder passwordEncoder,
                                       RolePermissionService rolePermissionService,
                                       TenantSubscriptionService tenantSubscriptionService) {
        this.tenantRepository = tenantRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.locationRepository = locationRepository;
        this.passwordEncoder = passwordEncoder;
        this.rolePermissionService = rolePermissionService;
        this.tenantSubscriptionService = tenantSubscriptionService;
    }

    @Transactional
    public TenantOnboardingService.OnboardingResult provision(SignupRequest request, UUID tenantId, String slug) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName(request.companyName());
        tenant.setSlug(slug);
        tenant.setStatus("ACTIVE");
        tenantRepository.save(tenant);

        tenantSettingsRepository.save(TenantSettings.withDefaults(tenantId));
        tenantSubscriptionService.insertDefaults(tenantId);

        List<Role> roles = new ArrayList<>();
        for (String code : DEFAULT_ROLES) {
            Role role = new Role();
            role.setTenantId(tenantId);
            role.setCode(code);
            role.setNetworkAccessLevel(com.invsys.domain.NetworkAccessLevel.defaultForRole(code));
            roles.add(roleRepository.save(role));
        }
        rolePermissionService.seedBaselineForTenant(tenantId, roles);

        User owner = new User();
        owner.setTenantId(tenantId);
        owner.setEmail(request.email().toLowerCase(Locale.ROOT));
        owner.setDisplayName(request.displayName());
        owner.setPasswordHash(passwordEncoder.encode(request.password()));
        owner.setStatus("ACTIVE");
        userRepository.save(owner);

        Role ownerRole = roles.stream().filter(r -> "OWNER".equals(r.getCode())).findFirst().orElseThrow();
        UserRole userRole = new UserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(owner.getId());
        userRole.setRoleId(ownerRole.getId());
        userRoleRepository.save(userRole);

        Location warehouse = new Location();
        warehouse.setTenantId(tenantId);
        warehouse.setType("WAREHOUSE");
        warehouse.setCode("WH-01");
        warehouse.setName("Main Warehouse");
        warehouse.setPath("/WH-01");
        locationRepository.save(warehouse);

        TenantContext.setUserId(owner.getId());
        return new TenantOnboardingService.OnboardingResult(tenant, owner, List.of("OWNER"));
    }

    @Transactional
    public UUID createTenant(String name, String slug, UUID tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName(name);
        tenant.setSlug(slug);
        tenant.setStatus("ACTIVE");
        tenantRepository.saveAndFlush(tenant);
        tenantSubscriptionService.insertDefaults(tenantId);
        return tenantId;
    }
}
