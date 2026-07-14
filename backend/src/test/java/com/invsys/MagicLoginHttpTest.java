package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MagicLoginHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void magicLoginIssuesTokenAndConsumesOnce() throws Exception {
        String slug = "magic-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "owner@" + slug + ".test";
        authService.signup(new SignupRequest("Magic Co", slug, email, "password123", "Owner"));

        MvcResult requestResult = mockMvc.perform(post("/api/v1/auth/magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.magicToken").isNotEmpty())
                .andReturn();

        String body = requestResult.getResponse().getContentAsString();
        String magicToken = body.replaceAll("(?s).*\"magicToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(magicToken).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/magic-login/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + magicToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/magic-login/consume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + magicToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void magicLoginUnknownEmailStillAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/magic-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }
}
