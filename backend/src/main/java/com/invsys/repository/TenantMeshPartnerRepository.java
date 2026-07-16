package com.invsys.repository;

import com.invsys.domain.TenantMeshPartner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMeshPartnerRepository extends JpaRepository<TenantMeshPartner, UUID> {

    List<TenantMeshPartner> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<TenantMeshPartner> findByTenantIdAndPartnerTenantId(UUID tenantId, UUID partnerTenantId);

    Optional<TenantMeshPartner> findByTenantIdAndSupplierIdAndConnectionStatus(
            UUID tenantId, UUID supplierId, String connectionStatus);
}
