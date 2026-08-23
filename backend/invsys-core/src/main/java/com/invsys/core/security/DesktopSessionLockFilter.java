package com.invsys.core.security;

import com.invsys.core.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Rejects data-plane calls while the desktop session is idle-locked (423 SESSION_LOCKED).
 * {@code /auth/me}, lock, and unlock stay reachable so the overlay can hydrate and recover.
 */
@Component
public class DesktopSessionLockFilter extends OncePerRequestFilter {

    private final DesktopSessionLockStore lockStore;

    public DesktopSessionLockFilter(DesktopSessionLockStore lockStore) {
        this.lockStore = lockStore;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UUID userId = TenantContext.getUserId().orElse(null);
        if (userId != null && lockStore.isLocked(userId) && !isLockAllowlisted(request.getRequestURI())) {
            response.setStatus(HttpStatus.LOCKED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"SESSION_LOCKED\",\"status\":423,"
                            + "\"detail\":\"Desktop session is locked\",\"code\":\"SESSION_LOCKED\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean isLockAllowlisted(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.equals("/api/v1/auth/me")
                || uri.equals("/api/v1/auth/lock")
                || uri.equals("/api/v1/auth/unlock")
                || uri.equals("/api/v1/auth/desktop-unlock")
                || uri.equals("/api/v1/auth/desktop-unlock/options")
                || uri.equals("/api/v1/auth/logout")
                || uri.equals("/api/v1/auth/refresh");
    }
}
