package com.invsys.core.security;

import com.invsys.core.security.dto.TokenResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Issues rotating access / refresh JWTs as HttpOnly cookies (never in JSON bodies).
 */
@Service
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "invsys_access";
    public static final String REFRESH_COOKIE = "invsys_refresh";

    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final boolean secure;
    private final String sameSite;

    public AuthCookieService(
            @Value("${invsys.jwt.access-token-minutes:15}") long accessMinutes,
            @Value("${invsys.jwt.refresh-token-days:7}") long refreshDays,
            @Value("${invsys.security.cookie-secure:true}") boolean secure,
            @Value("${invsys.security.cookie-same-site:Strict}") String sameSite) {
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public void writeSessionCookies(HttpServletResponse response, TokenResponse tokens) {
        writeAccessCookie(response, tokens.accessToken(), accessTtl);
        writeRefreshCookie(response, tokens.refreshToken(), refreshTtl);
    }

    public void writeAccessCookie(HttpServletResponse response, String accessToken, Duration ttl) {
        ResponseCookie cookie = baseBuilder(ACCESS_COOKIE, accessToken)
                .maxAge(ttl)
                .path("/")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void writeTerminalAccessCookie(HttpServletResponse response, String accessToken, int expiresInSeconds) {
        writeAccessCookie(response, accessToken, Duration.ofSeconds(Math.max(expiresInSeconds, 60)));
    }

    public void writeRefreshCookie(HttpServletResponse response, String refreshToken, Duration ttl) {
        ResponseCookie cookie = baseBuilder(REFRESH_COOKIE, refreshToken)
                .maxAge(ttl)
                .path("/api/v1/auth")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearSessionCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", baseBuilder(ACCESS_COOKIE, "")
                .maxAge(Duration.ZERO)
                .path("/")
                .build()
                .toString());
        response.addHeader("Set-Cookie", baseBuilder(REFRESH_COOKIE, "")
                .maxAge(Duration.ZERO)
                .path("/api/v1/auth")
                .build()
                .toString());
    }

    public String readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_COOKIE);
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_COOKIE);
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String name, String value) {
        return ResponseCookie.from(name, value == null ? "" : value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite);
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }
}
