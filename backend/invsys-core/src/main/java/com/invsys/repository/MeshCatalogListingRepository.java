package com.invsys.repository;

import com.invsys.domain.MeshCatalogListing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeshCatalogListingRepository extends JpaRepository<MeshCatalogListing, UUID> {

    Optional<MeshCatalogListing> findByTenantIdAndVariantId(UUID tenantId, UUID variantId);

    List<MeshCatalogListing> findByTenantId(UUID tenantId);
}
