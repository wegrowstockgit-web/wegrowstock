package com.invsys.api;

import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.mesh.CrossTenantMeshBridgeService;
import com.invsys.service.MeshCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class MeshCatalogController {

    private final MeshCatalogService meshCatalogService;
    private final CrossTenantMeshBridgeService meshBridgeService;

    public MeshCatalogController(MeshCatalogService meshCatalogService,
                                 CrossTenantMeshBridgeService meshBridgeService) {
        this.meshCatalogService = meshCatalogService;
        this.meshBridgeService = meshBridgeService;
    }

    @GetMapping("/api/v1/settings/mesh/partners")
    public List<MeshCatalogService.MeshPartnerSummary> partners() {
        return meshCatalogService.listPartners();
    }

    @GetMapping("/api/v1/settings/mesh/partners/{partnerTenantId}/catalog")
    public List<MeshCatalogService.PartnerSkuRow> partnerCatalog(@PathVariable UUID partnerTenantId) {
        return meshCatalogService.listPartnerCatalog(partnerTenantId);
    }

    @GetMapping("/api/v1/settings/mesh/partners/{partnerTenantId}/mappings")
    public List<MeshCatalogService.CatalogMappingRow> mappings(@PathVariable UUID partnerTenantId) {
        return meshCatalogService.listMappings(partnerTenantId);
    }

    @PutMapping("/api/v1/settings/mesh/partners/{partnerTenantId}/mappings")
    public List<MeshCatalogService.CatalogMappingRow> upsertMappings(
            @PathVariable UUID partnerTenantId,
            @Valid @RequestBody List<MappingRequest> body) {
        List<MeshCatalogService.MappingUpsert> upserts = body == null
                ? List.of()
                : body.stream()
                        .map(r -> new MeshCatalogService.MappingUpsert(r.localVariantId(), r.partnerVariantId()))
                        .toList();
        return meshCatalogService.upsertMappings(partnerTenantId, upserts);
    }

    @GetMapping("/api/v1/mesh/discover")
    public List<MeshCatalogService.DiscoverListing> discover() {
        return meshCatalogService.discoverPublished();
    }

    @GetMapping("/api/v1/mesh/network")
    public List<MeshCatalogService.NetworkRelationship> network() {
        return meshCatalogService.listNetwork();
    }

    @GetMapping("/api/v1/mesh/catalog")
    public List<MeshCatalogService.SharedCatalogRow> sharedCatalog() {
        return meshCatalogService.listSharedCatalog();
    }

    @PutMapping("/api/v1/mesh/catalog/{variantId}")
    public MeshCatalogService.SharedCatalogRow upsertListing(
            @PathVariable UUID variantId,
            @Valid @RequestBody ListingRequest body) {
        return meshCatalogService.upsertListing(variantId, body.published(), body.meshWholesalePrice());
    }

    @PostMapping("/api/v1/mesh/connections/request")
    public ConnectionResponse requestConnection(@Valid @RequestBody ConnectionRequest body) {
        BootstrapJdbc.MeshPartnerRow row = meshBridgeService.requestConnection(
                body.partnerTenantId(), body.variantId());
        return toConnection(row);
    }

    @PostMapping("/api/v1/mesh/connections/{id}/approve")
    public ConnectionResponse approveConnection(@PathVariable UUID id) {
        return toConnection(meshBridgeService.approveConnection(id));
    }

    private static ConnectionResponse toConnection(BootstrapJdbc.MeshPartnerRow row) {
        return new ConnectionResponse(
                row.id(),
                row.tenantId(),
                row.partnerTenantId(),
                row.supplierId(),
                row.customerId(),
                row.connectionStatus());
    }

    public record MappingRequest(
            @NotNull UUID localVariantId,
            UUID partnerVariantId) {
    }

    public record ConnectionRequest(UUID partnerTenantId, UUID variantId) {
    }

    public record ListingRequest(
            boolean published,
            BigDecimal meshWholesalePrice) {
    }

    public record ConnectionResponse(
            UUID id,
            UUID tenantId,
            UUID partnerTenantId,
            UUID supplierId,
            UUID customerId,
            String connectionStatus) {
    }
}
