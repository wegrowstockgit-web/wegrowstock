package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.Role;
import com.invsys.domain.Supplier;
import com.invsys.domain.SupplierUserMapping;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.repository.SupplierUserMappingRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SupplierPortalHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired SupplierRepository supplierRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired SupplierUserMappingRepository supplierUserMappingRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void supplierCanListOpenPurchaseOrders() throws Exception {
        String slug = "sup-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Supplier Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Acme Vendor");
        supplier = supplierRepository.save(supplier);

        PurchaseOrder open = new PurchaseOrder();
        open.setTenantId(tenantId);
        open.setSupplierId(supplier.getId());
        open.setNumber("PO-OPEN-1");
        open.setStatus("SUBMITTED");
        purchaseOrderRepository.save(open);

        PurchaseOrder closed = new PurchaseOrder();
        closed.setTenantId(tenantId);
        closed.setSupplierId(supplier.getId());
        closed.setNumber("PO-CLOSED-1");
        closed.setStatus("RECEIVED");
        purchaseOrderRepository.save(closed);

        Role supplierRole = roleRepository.findByTenantIdAndCode(tenantId, "SUPPLIER")
                .orElseGet(() -> {
                    Role created = new Role();
                    created.setTenantId(tenantId);
                    created.setCode("SUPPLIER");
                    return roleRepository.save(created);
                });

        User vendorUser = new User();
        vendorUser.setTenantId(tenantId);
        vendorUser.setEmail("vendor@" + slug + ".test");
        vendorUser.setDisplayName("Vendor");
        vendorUser.setPasswordHash(passwordEncoder.encode("password123"));
        vendorUser.setStatus("ACTIVE");
        vendorUser = userRepository.save(vendorUser);

        UserRole userRole = new UserRole();
        userRole.setTenantId(tenantId);
        userRole.setUserId(vendorUser.getId());
        userRole.setRoleId(supplierRole.getId());
        userRoleRepository.save(userRole);

        SupplierUserMapping mapping = new SupplierUserMapping();
        mapping.setTenantId(tenantId);
        mapping.setSupplierId(supplier.getId());
        mapping.setUserId(vendorUser.getId());
        supplierUserMappingRepository.save(mapping);
        TenantContext.clear();

        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted("vendor@" + slug + ".test");

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        var accessCookie = loginResult.getResponse().getCookie(com.invsys.auth.AuthCookieService.ACCESS_COOKIE);
        org.assertj.core.api.Assertions.assertThat(accessCookie).isNotNull();

        mockMvc.perform(get("/api/v1/supplier-portal/purchase-orders")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value("PO-OPEN-1"))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"));
    }
}
