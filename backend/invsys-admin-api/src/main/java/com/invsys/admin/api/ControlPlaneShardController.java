package com.invsys.admin.api;

import com.invsys.admin.service.AdminShardRoutingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/control-plane/shards")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneShardController {

    private final AdminShardRoutingService adminShardRoutingService;

    public ControlPlaneShardController(AdminShardRoutingService adminShardRoutingService) {
        this.adminShardRoutingService = adminShardRoutingService;
    }

    @GetMapping
    public List<AdminShardRoutingService.ShardRouteView> list() {
        return adminShardRoutingService.listAll();
    }

    @GetMapping("/{tenantId}")
    public AdminShardRoutingService.ShardRouteView get(@PathVariable UUID tenantId) {
        return adminShardRoutingService.get(tenantId);
    }

    @PutMapping("/{tenantId}")
    public AdminShardRoutingService.ShardRouteView put(
            @PathVariable UUID tenantId,
            @RequestBody AdminShardRoutingService.ShardUpsertRequest request) {
        return adminShardRoutingService.upsert(tenantId, request);
    }
}
