package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.subscription.AppModule;
import com.invsys.service.TenantSubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PosSessionHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TenantSubscriptionService tenantSubscriptionService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void session_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/pos/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void session_returnsOrganizationLanguageAndWmsCurrencyWhenPosEnabled() throws Exception {
        TokenResponse owner = signup("pos-cfg");

        mockMvc.perform(patch("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locale_language":"es","currency":"EUR"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/pos/session")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Accept-Language", "en-US")
                        .param("timezone", "America/Mexico_City")
                        .param("placeLanguage", "en")
                        .param("placeCurrency", "MXN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posEnabled").value(true))
                .andExpect(jsonPath("$.module").value("RETAIL_POS"))
                .andExpect(jsonPath("$.language").value("es"))
                .andExpect(jsonPath("$.languageSource").value("ORGANIZATION"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.currencySource").value("WMS"))
                .andExpect(jsonPath("$.placeCurrency").value("MXN"))
                .andExpect(jsonPath("$.taxRegionHint").value("MX"))
                .andExpect(jsonPath("$.companyName").value("POS Co"));
    }

    @Test
    void session_fallsBackToUserLanguageWhenOrganizationUnset() throws Exception {
        TokenResponse owner = signup("pos-user-lang");

        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredLanguage":"fr"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/pos/session")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Accept-Language", "en-GB")
                        .param("timezone", "Europe/London"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posEnabled").value(true))
                .andExpect(jsonPath("$.language").value("fr"))
                .andExpect(jsonPath("$.languageSource").value("USER"));
    }

    @Test
    void session_returnsDisabledWithoutApplyingWmsLocaleWhenModuleLocked() throws Exception {
        TokenResponse owner = signup("pos-locked");

        mockMvc.perform(patch("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locale_language":"fr","currency":"GBP"}
                                """))
                .andExpect(status().isOk());

        tenantSubscriptionService.replaceEnabledModules(owner.tenantId(), List.of(AppModule.CORE));

        mockMvc.perform(get("/api/v1/pos/session")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("Accept-Language", "es-MX")
                        .param("placeLanguage", "es")
                        .param("placeCurrency", "MXN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posEnabled").value(false))
                .andExpect(jsonPath("$.language").value("es"))
                .andExpect(jsonPath("$.languageSource").value("PLACE"))
                .andExpect(jsonPath("$.currency").value("MXN"))
                .andExpect(jsonPath("$.currencySource").value("PLACE"));
    }

    private TokenResponse signup(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return authService.signup(new SignupRequest(
                "POS Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
    }
}
