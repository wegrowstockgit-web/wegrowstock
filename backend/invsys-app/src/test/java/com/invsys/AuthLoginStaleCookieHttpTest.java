package com.invsys;

import com.invsys.core.security.AuthCookieService;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthLoginStaleCookieHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void loginSucceedsEvenWhenExpiredAccessCookiePresent() throws Exception {
        String slug = "stale-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "owner@" + slug + ".test";
        authService.signup(new SignupRequest("Stale Cookie Co", slug, email, "password123", "Owner"));

        MockCookie stale = new MockCookie(AuthCookieService.ACCESS_COOKIE, "not.a.valid.jwt");
        stale.setHttpOnly(true);
        stale.setPath("/");

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(stale)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().exists(AuthCookieService.ACCESS_COOKIE))
                .andReturn();

        var access = login.getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
        mockMvc.perform(get("/api/v1/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }
}
