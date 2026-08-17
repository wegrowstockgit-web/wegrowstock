package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RetailPosSettingsHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getExposesPosDefaultsThenPutAndPatchPersistThem() throws Exception {
        TokenResponse owner = signup("pos-set");

        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pos_default_currency").value("USD"))
                .andExpect(jsonPath("$.pos_require_blind_closeout").value(false))
                .andExpect(jsonPath("$.pos_enable_cfdi_invoicing").value(false))
                .andExpect(jsonPath("$.pos_receipt_header").value(""))
                .andExpect(jsonPath("$.pos_receipt_footer").value(""));

        mockMvc.perform(put("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pos_receipt_header": "Pacific Parts\\nRFC PAC010101AAA",
                                  "pos_receipt_footer": "No returns after 14 days",
                                  "pos_default_currency": "MXN",
                                  "pos_require_blind_closeout": true,
                                  "pos_enable_cfdi_invoicing": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pos_default_currency").value("MXN"))
                .andExpect(jsonPath("$.pos_require_blind_closeout").value(true))
                .andExpect(jsonPath("$.pos_enable_cfdi_invoicing").value(true))
                .andExpect(jsonPath("$.pos_receipt_header").value("Pacific Parts\nRFC PAC010101AAA"))
                .andExpect(jsonPath("$.pos_receipt_footer").value("No returns after 14 days"));

        mockMvc.perform(patch("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pos_enable_cfdi_invoicing": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pos_enable_cfdi_invoicing").value(false))
                .andExpect(jsonPath("$.pos_default_currency").value("MXN"))
                .andExpect(jsonPath("$.pos_require_blind_closeout").value(true));

        mockMvc.perform(get("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pos_default_currency").value("MXN"))
                .andExpect(jsonPath("$.pos_require_blind_closeout").value(true))
                .andExpect(jsonPath("$.pos_enable_cfdi_invoicing").value(false));
    }

    @Test
    void putRejectsUnsupportedPosCurrency() throws Exception {
        TokenResponse owner = signup("pos-bad-ccy");

        mockMvc.perform(put("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pos_default_currency": "EUR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("POS_CURRENCY_UNSUPPORTED"));
    }

    private TokenResponse signup(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return authService.signup(new SignupRequest(
                "POS Settings Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
    }
}
