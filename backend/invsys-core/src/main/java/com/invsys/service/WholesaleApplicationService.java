package com.invsys.service;

import com.invsys.api.dto.WholesaleApplicationResponse;
import com.invsys.api.dto.WholesaleApplyRequest;
import com.invsys.core.common.ApiException;
import com.invsys.core.security.MagicLoginService;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.CustomerPriceTier;
import com.invsys.domain.CustomerUserMapping;
import com.invsys.domain.Role;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.WholesaleApplication;
import com.invsys.modules.sales.domain.WholesaleApplicationStatus;
import com.invsys.modules.sales.repository.CustomerPriceTierRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.CustomerUserMappingRepository;
import com.invsys.modules.sales.repository.WholesaleApplicationRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class WholesaleApplicationService {

    private final WholesaleApplicationRepository applicationRepository;
    private final CustomerRepository customerRepository;
    private final CustomerPriceTierRepository priceTierRepository;
    private final CustomerUserMappingRepository mappingRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MagicLoginService magicLoginService;
    private final BootstrapJdbc bootstrapJdbc;
    private final PublicShowroomTenantResolver tenantResolver;

    public WholesaleApplicationService(WholesaleApplicationRepository applicationRepository,
                                       CustomerRepository customerRepository,
                                       CustomerPriceTierRepository priceTierRepository,
                                       CustomerUserMappingRepository mappingRepository,
                                       UserRepository userRepository,
                                       UserRoleRepository userRoleRepository,
                                       RoleRepository roleRepository,
                                       PasswordEncoder passwordEncoder,
                                       MagicLoginService magicLoginService,
                                       BootstrapJdbc bootstrapJdbc,
                                       PublicShowroomTenantResolver tenantResolver) {
        this.applicationRepository = applicationRepository;
        this.customerRepository = customerRepository;
        this.priceTierRepository = priceTierRepository;
        this.mappingRepository = mappingRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.magicLoginService = magicLoginService;
        this.bootstrapJdbc = bootstrapJdbc;
        this.tenantResolver = tenantResolver;
    }

    @Transactional
    public WholesaleApplicationResponse apply(WholesaleApplyRequest request, String tenantSlugHeader) {
        UUID tenantId = tenantResolver.resolve(tenantSlugHeader, request.tenantSlug());
        TenantContext.setTenantId(tenantId);
        String email = normalizeEmail(request.email());
        if (applicationRepository.existsByTenantIdAndEmailIgnoreCaseAndStatus(
                tenantId, email, WholesaleApplicationStatus.PENDING.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_EXISTS",
                    "A wholesale application for this email is already under review");
        }
        if (userRepository.findByTenantIdAndEmail(tenantId, email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED",
                    "This email already has a wholesale account");
        }

        WholesaleApplication application = new WholesaleApplication();
        application.setTenantId(tenantId);
        application.setCompanyName(request.companyName().trim());
        application.setTaxId(request.taxId().trim());
        application.setContactName(request.contactName().trim());
        application.setEmail(email);
        application.setPhone(blankToNull(request.phone()));
        application.setStatus(WholesaleApplicationStatus.PENDING.name());
        return toResponse(applicationRepository.save(application), null);
    }

    @Transactional(readOnly = true)
    public List<WholesaleApplicationResponse> list(String status) {
        UUID tenantId = TenantContext.requireTenantId();
        List<WholesaleApplication> rows = status == null || status.isBlank()
                ? applicationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : applicationRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status.trim().toUpperCase(Locale.ROOT));
        return rows.stream().map(row -> toResponse(row, null)).toList();
    }

    @Transactional
    public WholesaleApplicationResponse approve(UUID applicationId) {
        UUID tenantId = TenantContext.requireTenantId();
        WholesaleApplication application = applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Wholesale application not found"));
        if (!WholesaleApplicationStatus.PENDING.name().equals(application.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only pending applications can be approved");
        }

        CustomerPriceTier tier = resolveDefaultTier(tenantId);
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName(application.getCompanyName());
        customer.setEmail(application.getEmail());
        customer.setTaxId(application.getTaxId());
        customer.setCustomerStatus("ACTIVE");
        customer.setPaymentTerms("NET30");
        customer.setBillingAddress(new LinkedHashMap<>());
        customer.setShippingAddress(new LinkedHashMap<>());
        customer.setPriceTierId(tier.getId());
        customer = customerRepository.save(customer);

        User user = provisionBuyer(tenantId, application);
        if (mappingRepository.findByUserId(user.getId()).isEmpty()) {
            CustomerUserMapping mapping = new CustomerUserMapping();
            mapping.setTenantId(tenantId);
            mapping.setCustomerId(customer.getId());
            mapping.setUserId(user.getId());
            mappingRepository.save(mapping);
        }

        application.setStatus(WholesaleApplicationStatus.APPROVED.name());
        application.setCustomerId(customer.getId());
        applicationRepository.save(application);

        Map<String, Object> magic = magicLoginService.issueWelcomeMagicLink(
                tenantId, user.getId(), application.getEmail());
        Object token = magic.get("magicToken");
        return toResponse(application, token instanceof String s ? s : null);
    }

    private User provisionBuyer(UUID tenantId, WholesaleApplication application) {
        var existingAuth = bootstrapJdbc.findUserForAuthByEmail(application.getEmail());
        if (existingAuth.isPresent() && !existingAuth.get().tenantId().equals(tenantId)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_IN_USE",
                    "This email is already registered");
        }
        User user = userRepository.findByTenantIdAndEmail(tenantId, application.getEmail()).orElse(null);
        if (user == null) {
            user = new User();
            user.setTenantId(tenantId);
            user.setEmail(application.getEmail());
            user.setDisplayName(application.getContactName());
            user.setPhone(application.getPhone());
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setStatus("ACTIVE");
            user = userRepository.save(user);
        } else if (!"ACTIVE".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_INACTIVE",
                    "This email belongs to an inactive account");
        }

        Role role = roleRepository.findByTenantIdAndCode(tenantId, "B2B_CUSTOMER")
                .orElseGet(() -> {
                    Role created = new Role();
                    created.setTenantId(tenantId);
                    created.setCode("B2B_CUSTOMER");
                    created.setNetworkAccessLevel(
                            com.invsys.domain.NetworkAccessLevel.defaultForRole("B2B_CUSTOMER"));
                    return roleRepository.save(created);
                });
        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getId());
        if (!roles.contains("B2B_CUSTOMER")) {
            UserRole userRole = new UserRole();
            userRole.setTenantId(tenantId);
            userRole.setUserId(user.getId());
            userRole.setRoleId(role.getId());
            userRoleRepository.save(userRole);
        }
        return user;
    }

    private CustomerPriceTier resolveDefaultTier(UUID tenantId) {
        List<CustomerPriceTier> existing = priceTierRepository.findByTenantIdOrderByNameAsc(tenantId);
        if (!existing.isEmpty()) {
            return existing.stream()
                    .filter(t -> "Wholesale".equalsIgnoreCase(t.getName()))
                    .findFirst()
                    .orElse(existing.getFirst());
        }
        CustomerPriceTier created = new CustomerPriceTier();
        created.setTenantId(tenantId);
        created.setName("Wholesale");
        created.setDiscountPercent(BigDecimal.ZERO);
        return priceTierRepository.save(created);
    }

    private static WholesaleApplicationResponse toResponse(WholesaleApplication application, String magicToken) {
        return new WholesaleApplicationResponse(
                application.getId(),
                application.getCompanyName(),
                application.getTaxId(),
                application.getContactName(),
                application.getEmail(),
                application.getPhone(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getCustomerId(),
                magicToken);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
