package com.invsys.training;

import com.invsys.core.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * When {@code X-Training-Mode: true}, swaps {@link TenantContext} to a shadow sandbox tenant
 * for the duration of the MVC handler (runs inside JWT filter, so tenant is already bound).
 */
@Component
@ConditionalOnProperty(name = "invsys.features.training.enabled", havingValue = "true", matchIfMissing = true)
public class ShadowTenantInterceptor implements HandlerInterceptor {

    public static final String HEADER_TRAINING_MODE = "X-Training-Mode";
    static final String ATTR_PREVIOUS_TENANT = "invsys.training.previousTenantId";

    private final TrainingSandboxService sandboxService;

    public ShadowTenantInterceptor(TrainingSandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!isTrainingRequest(request)) {
            return true;
        }
        UUID liveTenant = TenantContext.getTenantId().orElse(null);
        if (liveTenant == null) {
            return true;
        }
        UUID sandbox = sandboxService.resolveOrCreateSandboxTenant(
                liveTenant, TenantContext.getUserId().orElse(null));
        request.setAttribute(ATTR_PREVIOUS_TENANT, liveTenant);
        TenantContext.setTenantId(sandbox);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        Object previous = request.getAttribute(ATTR_PREVIOUS_TENANT);
        if (previous instanceof UUID previousTenant) {
            TenantContext.setTenantId(previousTenant);
            request.removeAttribute(ATTR_PREVIOUS_TENANT);
        }
    }

    static boolean isTrainingRequest(HttpServletRequest request) {
        String value = request.getHeader(HEADER_TRAINING_MODE);
        if (value == null || value.isBlank()) {
            return false;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }
}
