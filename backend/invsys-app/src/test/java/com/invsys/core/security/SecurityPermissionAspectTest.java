package com.invsys.core.security;

import com.invsys.domain.Role;
import com.invsys.repository.RoleRepository;
import com.invsys.service.RolePermissionService;
import com.invsys.core.tenancy.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityPermissionAspectTest {

    private static final UUID TENANT = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID VIEWER_ROLE_ID = UUID.fromString("c0000000-0000-4000-8000-000000000010");

    @Mock RoleRepository roleRepository;
    @Mock RolePermissionService rolePermissionService;
    @Mock ProceedingJoinPoint joinPoint;

    SecurityPermissionAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new SecurityPermissionAspect(roleRepository, rolePermissionService);
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerBypassesPermissionMatrix() throws Throwable {
        setAuth("OWNER");
        RequirePermission annotation = sampleMethod().getAnnotation(RequirePermission.class);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.enforcePermission(joinPoint, annotation);

        assertThat(result).isEqualTo("ok");
        verify(rolePermissionService, never()).isGrantedForRoles(org.mockito.ArgumentMatchers.any(), eq(PermissionKeys.INVENTORY_COST_VIEW));
    }

    @Test
    void allowsWhenNoMatrixRowsExist() throws Throwable {
        setAuth("VIEWER");
        Role role = role("VIEWER", VIEWER_ROLE_ID);
        when(roleRepository.findByTenantIdAndCode(TENANT, "VIEWER")).thenReturn(Optional.of(role));
        when(rolePermissionService.isGrantedForRoles(List.of(VIEWER_ROLE_ID), PermissionKeys.INVENTORY_COST_VIEW))
                .thenReturn(true);
        RequirePermission annotation = sampleMethod().getAnnotation(RequirePermission.class);
        when(joinPoint.proceed()).thenReturn("allowed");

        Object result = aspect.enforcePermission(joinPoint, annotation);

        assertThat(result).isEqualTo("allowed");
    }

    @Test
    void deniesWhenExplicitlyRevoked() throws Throwable {
        setAuth("VIEWER");
        Role role = role("VIEWER", VIEWER_ROLE_ID);
        when(roleRepository.findByTenantIdAndCode(TENANT, "VIEWER")).thenReturn(Optional.of(role));
        when(rolePermissionService.isGrantedForRoles(List.of(VIEWER_ROLE_ID), PermissionKeys.INVENTORY_COST_VIEW))
                .thenReturn(false);
        RequirePermission annotation = sampleMethod().getAnnotation(RequirePermission.class);

        assertThatThrownBy(() -> aspect.enforcePermission(joinPoint, annotation))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(PermissionKeys.INVENTORY_COST_VIEW);
    }

    @Test
    void requiresAuthentication() throws Throwable {
        SecurityContextHolder.clearContext();
        RequirePermission annotation = sampleMethod().getAnnotation(RequirePermission.class);

        assertThatThrownBy(() -> aspect.enforcePermission(joinPoint, annotation))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    private void setAuth(String roleCode) {
        var auth = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleCode)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static Role role(String code, UUID id) {
        Role role = new Role();
        role.setId(id);
        role.setTenantId(TENANT);
        role.setCode(code);
        return role;
    }

    private static Method sampleMethod() throws NoSuchMethodException {
        return Sample.class.getDeclaredMethod("costView");
    }

    static class Sample {
        @RequirePermission(PermissionKeys.INVENTORY_COST_VIEW)
        void costView() {
        }
    }
}
