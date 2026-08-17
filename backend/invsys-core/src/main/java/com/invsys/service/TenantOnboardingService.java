package com.invsys.service;

import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.common.ApiException;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.Role;
import com.invsys.domain.Tenant;
import com.invsys.domain.TenantSettings;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TenantOnboardingService {

    private static final List<String> DEFAULT_ROLES = List.of(
            "OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER",
            "RETAIL_CASHIER", "RETAIL_MANAGER");

    private final TenantProvisioningService provisioningService;

    public TenantOnboardingService(TenantProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    public OnboardingResult signup(SignupRequest request) {
        String slug = request.slug().toLowerCase(Locale.ROOT).trim();
        UUID tenantId = UUID.randomUUID();
        TenantContext.setBootstrap(true);
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(null);
        try {
            return provisioningService.provision(request, tenantId, slug);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "SLUG_EXISTS", "Tenant slug already exists");
        } finally {
            TenantContext.setBootstrap(false);
        }
    }

    public record OnboardingResult(Tenant tenant, User user, List<String> roles) {
    }
}
