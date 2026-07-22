package com.invsys.api;

import com.invsys.service.DashboardSseHub;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Server-Sent Events feed for office dashboard / cycle-count live refresh.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER','PICKER')")
public class DashboardStreamController {

    private final DashboardSseHub sseHub;

    public DashboardStreamController(DashboardSseHub sseHub) {
        this.sseHub = sseHub;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        UUID tenantId = TenantContext.requireTenantId();
        return sseHub.subscribe(tenantId);
    }
}
