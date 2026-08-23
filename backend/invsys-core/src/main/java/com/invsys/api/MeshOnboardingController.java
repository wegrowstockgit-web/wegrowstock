package com.invsys.api;

import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.mesh.CrossTenantMeshBridgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mesh")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class MeshOnboardingController {

    private final BootstrapJdbc bootstrapJdbc;
    private final CrossTenantMeshBridgeService meshBridgeService;

    public MeshOnboardingController(BootstrapJdbc bootstrapJdbc,
                                    CrossTenantMeshBridgeService meshBridgeService) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.meshBridgeService = meshBridgeService;
    }

    @GetMapping("/directory")
    public List<DirectoryHit> directory(@RequestParam(required = false) String q) {
        return bootstrapJdbc.searchMeshDirectory(TenantContext.requireTenantId(), q).stream()
                .map(row -> new DirectoryHit(
                        row.tenantId(),
                        row.name(),
                        row.slug(),
                        row.verified(),
                        row.catalogPublished()))
                .toList();
    }

    @PostMapping("/handshake/initiate")
    public CrossTenantMeshBridgeService.HandshakeResult initiate(
            @Valid @RequestBody HandshakeRequest request) {
        return meshBridgeService.initiateHandshake(request.partnerTenantId());
    }

    public record DirectoryHit(
            UUID tenantId,
            String name,
            String slug,
            boolean verified,
            boolean catalogPublished
    ) {
    }

    public record HandshakeRequest(@NotNull UUID partnerTenantId) {
    }
}
