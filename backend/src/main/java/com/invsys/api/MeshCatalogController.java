package com.invsys.api;

import com.invsys.service.MeshCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1/settings/mesh")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class MeshCatalogController {

    private final MeshCatalogService meshCatalogService;

    public MeshCatalogController(MeshCatalogService meshCatalogService) {
        this.meshCatalogService = meshCatalogService;
    }

    @GetMapping("/partners")
    public List<MeshCatalogService.MeshPartnerSummary> partners() {
        return meshCatalogService.listPartners();
    }

    @GetMapping("/partners/{partnerTenantId}/catalog")
    public List<MeshCatalogService.PartnerSkuRow> partnerCatalog(@PathVariable UUID partnerTenantId) {
        return meshCatalogService.listPartnerCatalog(partnerTenantId);
    }

    @GetMapping("/partners/{partnerTenantId}/mappings")
    public List<MeshCatalogService.CatalogMappingRow> mappings(@PathVariable UUID partnerTenantId) {
        return meshCatalogService.listMappings(partnerTenantId);
    }

    @PutMapping("/partners/{partnerTenantId}/mappings")
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

    public record MappingRequest(
            @NotNull UUID localVariantId,
            UUID partnerVariantId) {
    }
}
