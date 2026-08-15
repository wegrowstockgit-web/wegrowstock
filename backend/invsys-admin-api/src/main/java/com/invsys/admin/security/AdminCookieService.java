package com.invsys.admin.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AdminCookieService {

    public static final String ACCESS_COOKIE = "invsys_admin_access";
    public static final String REFRESH_COOKIE = "invsys_admin_refresh";

    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final boolean secure;

    public AdminCookieService(
            @Value("${invsys.jwt.access-token-minutes:15}") long accessMinutes,
            @Value("${invsys.jwt.refresh-token-days:7}") long refreshDays,
            @Value("${invsys.security.cookie-secure:true}") boolean secure) {
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
        this.secure = secure;
    }

    public void writeSessionCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        writeAccessCookie(response, accessToken);
        writeRefreshCookie(response, refreshToken);
    }

    public void writeAccessCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = baseBuilder(ACCESS_COOKIE, accessToken)
                .maxAge(accessTtl)
                .path("/")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = baseBuilder(REFRESH_COOKIE, refreshToken)
                .maxAge(refreshTtl)
                .path("/api/v1/control-plane/auth")
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
                .path("/api/v1/control-plane/auth")
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
                .sameSite("Strict");
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
