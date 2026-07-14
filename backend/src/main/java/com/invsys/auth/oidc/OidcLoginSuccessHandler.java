package com.invsys.auth.oidc;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.common.ApiException;
import com.invsys.repository.UserRepository;
import com.invsys.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@Component
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final String frontendUrl;

    public OidcLoginSuccessHandler(AuthService authService,
                                   UserRepository userRepository,
                                   @Value("${invsys.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported authentication");
            return;
        }
        UUID tenantId = UUID.fromString(token.getAuthorizedClientRegistrationId());
        OAuth2User principal = token.getPrincipal();
        String email = principal.getAttribute("email");
        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email claim missing from identity provider");
            return;
        }

        TenantContext.setTenantId(tenantId);
        try {
            var user = userRepository.findByTenantIdAndEmail(tenantId, email)
                    .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "USER_NOT_PROVISIONED",
                            "No user account exists for this email in the tenant"));
            TokenResponse tokens = authService.completeLogin(user.getId());
            String redirect = UriComponentsBuilder.fromUriString(frontendUrl + "/login")
                    .queryParam("sso", "1")
                    .queryParam("accessToken", tokens.accessToken())
                    .queryParam("refreshToken", tokens.refreshToken())
                    .queryParam("tenantId", tokens.tenantId().toString())
                    .queryParam("userId", tokens.userId().toString())
                    .build()
                    .encode()
                    .toUriString();
            response.sendRedirect(redirect);
        } finally {
            TenantContext.clear();
        }
    }
}
