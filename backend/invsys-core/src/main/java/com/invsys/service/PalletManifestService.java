package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.PalletManifest;
import com.invsys.domain.PalletManifestItem;
import com.invsys.modules.inventory.domain.LicensePlate;
import com.invsys.modules.inventory.repository.LicensePlateRepository;
import com.invsys.repository.PalletManifestItemRepository;
import com.invsys.repository.PalletManifestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PalletManifestService {

    private final PalletManifestRepository manifestRepository;
    private final PalletManifestItemRepository itemRepository;
    private final SsccGeneratorService ssccGeneratorService;
    private final LicensePlateRepository licensePlateRepository;

    public PalletManifestService(PalletManifestRepository manifestRepository,
                                 PalletManifestItemRepository itemRepository,
                                 SsccGeneratorService ssccGeneratorService,
                                 LicensePlateRepository licensePlateRepository) {
        this.manifestRepository = manifestRepository;
        this.itemRepository = itemRepository;
        this.ssccGeneratorService = ssccGeneratorService;
        this.licensePlateRepository = licensePlateRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PalletManifest> findActiveBuilding() {
        UUID tenantId = TenantContext.requireTenantId();
        return manifestRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "BUILDING");
    }

    @Transactional(readOnly = true)
    public List<PalletManifest> listManifests() {
        return manifestRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId());
    }

    @Transactional(readOnly = true)
    public PalletManifest getManifest(UUID palletId) {
        return requireManifest(palletId);
    }

    @Transactional(readOnly = true)
    public List<PalletManifestItem> listItems(UUID palletId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireManifest(palletId);
        return itemRepository.findByTenantIdAndPalletId(tenantId, palletId);
    }

    @Transactional
    public PalletManifest createManifest(UUID warehouseId, String carrier) {
        UUID tenantId = TenantContext.requireTenantId();
        PalletManifest manifest = new PalletManifest();
        manifest.setTenantId(tenantId);
        manifest.setWarehouseId(warehouseId);
        manifest.setCarrierName(carrier);
        manifest.setStatus("BUILDING");
        manifest.setSscc18(ssccGeneratorService.nextSscc());
        return manifestRepository.save(manifest);
    }

    @Transactional
    public PalletManifestItem addItem(UUID palletId, UUID lpnId, UUID shipmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        PalletManifest manifest = requireManifest(palletId);
        if (!"BUILDING".equals(manifest.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PALLET_NOT_BUILDING",
                    "Cannot add items to a " + manifest.getStatus() + " pallet manifest");
        }

        PalletManifestItem item = new PalletManifestItem();
        item.setTenantId(tenantId);
        item.setPalletId(palletId);
        item.setLpnId(lpnId);
        item.setShipmentId(shipmentId);
        return itemRepository.save(item);
    }

    @Transactional
    public PalletManifestItem addItemByLpnBarcode(UUID palletId, String lpnBarcode) {
        UUID tenantId = TenantContext.requireTenantId();
        String barcode = lpnBarcode != null ? lpnBarcode.trim() : "";
        LicensePlate lpn = licensePlateRepository.findByTenantIdAndLpnBarcode(tenantId, barcode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LPN_NOT_FOUND",
                        "License plate not found"));
        return addItem(palletId, lpn.getId(), null);
    }

    @Transactional
    public PalletManifest seal(UUID palletId, String bolNumber) {
        PalletManifest manifest = requireManifest(palletId);
        if (!"BUILDING".equals(manifest.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PALLET_NOT_BUILDING",
                    "Pallet manifest is not in BUILDING status");
        }
        if (manifest.getSscc18() == null || manifest.getSscc18().isBlank()) {
            manifest.setSscc18(ssccGeneratorService.nextSscc());
        }
        manifest.setBolNumber(bolNumber != null && !bolNumber.isBlank()
                ? bolNumber.trim()
                : "BOL-" + palletId.toString().substring(0, 8).toUpperCase());
        manifest.setStatus("SEALED");
        return manifestRepository.save(manifest);
    }

    @Transactional
    public PalletManifest dispatch(UUID palletId) {
        PalletManifest manifest = requireManifest(palletId);
        if (!"SEALED".equals(manifest.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PALLET_NOT_SEALED",
                    "Pallet manifest must be SEALED before dispatch");
        }
        manifest.setStatus("DISPATCHED");
        return manifestRepository.save(manifest);
    }

    private PalletManifest requireManifest(UUID palletId) {
        UUID tenantId = TenantContext.requireTenantId();
        return manifestRepository.findByTenantIdAndId(tenantId, palletId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PALLET_NOT_FOUND",
                        "Pallet manifest not found"));
    }
}
