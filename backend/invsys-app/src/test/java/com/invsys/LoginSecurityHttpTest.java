package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.LoginSecurityService;
import com.invsys.core.security.NetworkAccessPolicy;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.AuditLog;
import com.invsys.domain.NetworkAccessLevel;
import com.invsys.domain.PlatformAlert;
import com.invsys.domain.Role;
import com.invsys.domain.User;
import com.invsys.domain.UserRole;
import com.invsys.repository.AuditLogRepository;
import com.invsys.repository.PlatformAlertRepository;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LoginSecurityHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PlatformAlertRepository platformAlertRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void blockedLoginAndNewLocationAreAuditedAndAlerted() throws Exception {
        String slug = "iam-" + UUID.randomUUID().toString().substring(0, 8);
        String ownerEmail = "owner@" + slug + ".test";
        TokenResponse owner = authService.signup(new SignupRequest(
                "IAM Co", slug, ownerEmail, "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        assertThat(pickerRole.getNetworkAccessLevel()).isEqualTo(NetworkAccessLevel.STRICT_INTERNAL);

        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("ACTIVE");
        picker = userRepository.save(picker);
        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setUserId(picker.getId());
        ur.setRoleId(pickerRole.getId());
        userRoleRepository.save(ur);
        UUID pickerId = picker.getId();
        TenantContext.clear();

        mockMvc.perform(patch("/api/v1/settings/permissions/allowed-cidrs")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allowedCidrBlocks\":[\"10.0.0.0/8\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"picker@" + slug + ".test\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value(NetworkAccessPolicy.STRICT_DENIED_DETAIL));

        TenantContext.setTenantId(tenantId);
        List<AuditLog> afterBlock = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        AuditLog blocked = afterBlock.stream()
                .filter(row -> LoginSecurityService.ACTION_LOGIN_BLOCKED_CIDR.equals(row.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(blocked.getEntityType()).isEqualTo("USER");
        assertThat(blocked.getEntityId()).isEqualTo(pickerId);
        assertThat(blocked.getDiff().get("ip")).isEqualTo("203.0.113.40");
        assertThat(blocked.getDiff().get("location")).isEqualTo("Dallas, TX, US");
        TenantContext.clear();

        mockMvc.perform(patch("/api/v1/settings/permissions/network-access")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"" + pickerRole.getId()
                                + "\",\"networkAccessLevel\":\"ROAMING\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"picker@" + slug + ".test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        TenantContext.setTenantId(tenantId);
        assertThat(platformAlertRepository.findByTenantIdAndAlertTypeAndSourceSystemAndAcknowledgedAtIsNull(
                tenantId, LoginSecurityService.ALERT_NEW_LOGIN_LOCATION, pickerId.toString()))
                .isEmpty();
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.45");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"picker@" + slug + ".test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        TenantContext.setTenantId(tenantId);
        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<AuditLog> successes = logs.stream()
                .filter(row -> LoginSecurityService.ACTION_LOGIN_SUCCESS.equals(row.getAction())
                        && pickerId.equals(row.getEntityId()))
                .toList();
        assertThat(successes).hasSizeGreaterThanOrEqualTo(2);
        assertThat(successes.getFirst().getDiff().get("ip")).isEqualTo("198.51.100.45");
        assertThat(successes.getFirst().getDiff().get("location")).isEqualTo("London, England, GB");

        PlatformAlert alert = platformAlertRepository
                .findByTenantIdAndAlertTypeAndSourceSystemAndAcknowledgedAtIsNull(
                        tenantId, LoginSecurityService.ALERT_NEW_LOGIN_LOCATION, pickerId.toString())
                .orElseThrow();
        assertThat(alert.getSeverity()).isEqualTo("WARNING");
        assertThat(alert.getTitle()).contains("London, England, GB");
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/audit/entity/USER/" + pickerId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action == 'LOGIN_SUCCESS')]").exists())
                .andExpect(jsonPath("$[?(@.action == 'LOGIN_BLOCKED_CIDR')]").exists());
    }

    @Test
    void sessionFenceFromPublicIpWritesBlockedCidrAudit() throws Exception {
        String slug = "fence-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Fence IAM", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Role pickerRole = roleRepository.findByTenantIdAndCode(tenantId, "PICKER").orElseThrow();
        User picker = new User();
        picker.setTenantId(tenantId);
        picker.setEmail("picker@" + slug + ".test");
        picker.setDisplayName("Picker");
        picker.setPasswordHash(passwordEncoder.encode("password123"));
        picker.setStatus("ACTIVE");
        picker = userRepository.save(picker);
        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setUserId(picker.getId());
        ur.setRoleId(pickerRole.getId());
        userRoleRepository.save(ur);
        UUID pickerId = picker.getId();
        TenantContext.clear();

        mockMvc.perform(patch("/api/v1/settings/permissions/allowed-cidrs")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allowedCidrBlocks\":[\"10.0.0.0/8\"]}"))
                .andExpect(status().isOk());

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("10.1.2.3");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"picker@" + slug + ".test\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String access = login.getResponse().getCookie("invsys_access").getValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access)
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.40");
                            return request;
                        }))
                .andExpect(status().isForbidden());

        TenantContext.setTenantId(tenantId);
        assertThat(auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .anyMatch(row -> LoginSecurityService.ACTION_LOGIN_BLOCKED_CIDR.equals(row.getAction())
                        && pickerId.equals(row.getEntityId())
                        && "203.0.113.40".equals(String.valueOf(row.getDiff().get("ip"))));
        TenantContext.clear();
    }
}
