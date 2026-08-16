package com.invsys.api;

import com.invsys.api.dto.PortalCatalogItemResponse;
import com.invsys.api.dto.WholesaleApplicationResponse;
import com.invsys.api.dto.WholesaleApplyRequest;
import com.invsys.core.security.PermissionKeys;
import com.invsys.core.security.RequirePermission;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.service.PortalService;
import com.invsys.service.PublicShowroomTenantResolver;
import com.invsys.service.WholesaleApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class WholesaleApplicationController {

    private final WholesaleApplicationService wholesaleApplicationService;
    private final PortalService portalService;
    private final PublicShowroomTenantResolver tenantResolver;

    public WholesaleApplicationController(WholesaleApplicationService wholesaleApplicationService,
                                          PortalService portalService,
                                          PublicShowroomTenantResolver tenantResolver) {
        this.wholesaleApplicationService = wholesaleApplicationService;
        this.portalService = portalService;
        this.tenantResolver = tenantResolver;
    }

    @PostMapping("/api/v1/showroom/apply")
    public WholesaleApplicationResponse apply(@Valid @RequestBody WholesaleApplyRequest request,
                                              HttpServletRequest http) {
        return wholesaleApplicationService.apply(request, http.getHeader("X-Tenant-Slug"));
    }

    @GetMapping("/api/v1/showroom/catalog")
    public List<PortalCatalogItemResponse> publicCatalog(@RequestParam(required = false) String tenantSlug,
                                                         HttpServletRequest http) {
        UUID tenantId = tenantResolver.resolve(http.getHeader("X-Tenant-Slug"), tenantSlug);
        TenantContext.setTenantId(tenantId);
        return portalService.publicCatalog(tenantId);
    }

    @GetMapping("/api/v1/customers/applications")
    @RequirePermission(PermissionKeys.MANAGE_CUSTOMERS)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<WholesaleApplicationResponse> list(@RequestParam(required = false) String status) {
        return wholesaleApplicationService.list(status);
    }

    @PostMapping("/api/v1/customers/applications/{id}/approve")
    @RequirePermission(PermissionKeys.MANAGE_CUSTOMERS)
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public WholesaleApplicationResponse approve(@PathVariable UUID id) {
        return wholesaleApplicationService.approve(id);
    }
}
