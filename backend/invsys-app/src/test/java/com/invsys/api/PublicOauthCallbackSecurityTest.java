package com.invsys.api;

import com.invsys.AbstractIntegrationTest;
import com.invsys.TestDataHelper;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicOauthCallbackSecurityTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired BootstrapJdbc bootstrapJdbc;
    @Autowired TestDataHelper testDataHelper;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void callbackRequiresAuthorizationCode() throws Exception {
        UUID tenantId = testDataHelper.createTenant("OAuth Co", "oauth-" + UUID.randomUUID().toString().substring(0, 8));
        String state = UUID.randomUUID().toString().replace("-", "") + "state";
        bootstrapJdbc.insertOauthCallbackState(
                state, tenantId, "shopify", "{}", Instant.now().plusSeconds(300));

        mockMvc.perform(get("/api/v1/public/oauth/callback/shopify").param("state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION"));
    }

    @Test
    void successfulCallbackDoesNotEchoTenantId() throws Exception {
        UUID tenantId = testDataHelper.createTenant("OAuth2 Co", "oauth2-" + UUID.randomUUID().toString().substring(0, 8));
        String state = UUID.randomUUID().toString().replace("-", "") + "ok";
        bootstrapJdbc.insertOauthCallbackState(
                state, tenantId, "shopify", "{}", Instant.now().plusSeconds(300));

        mockMvc.perform(get("/api/v1/public/oauth/callback/shopify")
                        .param("state", state)
                        .param("code", "auth-code-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.tenantId").doesNotExist());
    }
}
