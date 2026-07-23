package com.invsys.core.security;

import com.invsys.domain.Role;
import com.invsys.repository.RoleRepository;
import com.invsys.service.RolePermissionService;
import com.invsys.core.tenancy.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Aspect
@Component
public class SecurityPermissionAspect {

    private final RoleRepository roleRepository;
    private final RolePermissionService rolePermissionService;

    public SecurityPermissionAspect(RoleRepository roleRepository,
                                    RolePermissionService rolePermissionService) {
        this.roleRepository = roleRepository;
        this.rolePermissionService = rolePermissionService;
    }

    @Around("@annotation(requirePermission)")
    public Object enforcePermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission)
            throws Throwable {
        String permissionKey = requirePermission.value();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            throw new AccessDeniedException("Authentication required");
        }

        List<String> roleCodes = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();

        if (roleCodes.contains("OWNER")) {
            return joinPoint.proceed();
        }

        UUID tenantId = TenantContext.requireTenantId();
        List<UUID> roleIds = new ArrayList<>();
        for (String code : roleCodes) {
            roleRepository.findByTenantIdAndCode(tenantId, code)
                    .map(Role::getId)
                    .ifPresent(roleIds::add);
        }

        if (rolePermissionService.isGrantedForRoles(roleIds, permissionKey)) {
            return joinPoint.proceed();
        }

        throw new AccessDeniedException("Missing permission: " + permissionKey);
    }
}
