package com.invsys.repository;

import com.invsys.domain.PalletManifestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PalletManifestItemRepository extends JpaRepository<PalletManifestItem, UUID> {

    List<PalletManifestItem> findByTenantIdAndPalletId(UUID tenantId, UUID palletId);
}
