package com.invsys.modules.inventory.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.inventory.domain.LicensePlate;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.inventory.repository.InventoryLevelDeltaFlushRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.inventory.repository.LicensePlateRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * On-the-fly palletization: mint LPNs, pack loose levels onto them, ship by LPN.
 */
@Service
public class LpnService {

    private final LicensePlateRepository licensePlateRepository;
    private final InventoryLevelRepository levelRepository;
    private final LocationRepository locationRepository;
    private final ProductVariantRepository variantRepository;
    private final AllocationRepository allocationRepository;
    private final InventoryService inventoryService;
    private final InventoryLevelDeltaFlushRepository deltaFlushRepository;
    private final EntityManager entityManager;

    public LpnService(LicensePlateRepository licensePlateRepository,
                      InventoryLevelRepository levelRepository,
                      LocationRepository locationRepository,
                      ProductVariantRepository variantRepository,
                      AllocationRepository allocationRepository,
                      InventoryService inventoryService,
                      InventoryLevelDeltaFlushRepository deltaFlushRepository,
                      EntityManager entityManager) {
        this.licensePlateRepository = licensePlateRepository;
        this.levelRepository = levelRepository;
        this.locationRepository = locationRepository;
        this.variantRepository = variantRepository;
        this.allocationRepository = allocationRepository;
        this.inventoryService = inventoryService;
        this.deltaFlushRepository = deltaFlushRepository;
        this.entityManager = entityManager;
    }

    /**
     * Mint an empty LPN ({@code LPN-} + Base36 timestamp) anywhere on the floor.
     */
    @Transactional
    public MintedLpn mint(UUID locationId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID resolvedLocationId = null;
        if (locationId != null) {
            Location location = locationRepository.findById(locationId)
                    .filter(l -> tenantId.equals(l.getTenantId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND",
                            "Location not found"));
            resolvedLocationId = location.getId();
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            String barcode = "LPN-" + Long.toString(System.currentTimeMillis() + attempt, 36).toUpperCase();
            if (licensePlateRepository.findByTenantIdAndLpnBarcode(tenantId, barcode).isPresent()) {
                continue;
            }
            LicensePlate lpn = new LicensePlate();
            lpn.setTenantId(tenantId);
            lpn.setLpnBarcode(barcode);
            lpn.setLocationId(resolvedLocationId);
            lpn.setStatus("OPEN");
            LicensePlate saved = licensePlateRepository.save(lpn);
            return new MintedLpn(saved.getId(), saved.getLpnBarcode(), saved.getLocationId(),
                    saved.getStatus(), buildLpnZpl(saved.getLpnBarcode()));
        }
        throw new ApiException(HttpStatus.CONFLICT, "LPN_MINT_FAILED", "Could not mint a unique LPN barcode");
    }

    /**
     * Bind inventory levels onto an LPN via TRANSFER_OUT / TRANSFER_IN with
     * reason {@code LPN_CONSOLIDATION} (levels are trigger-maintained from the ledger).
     */
    @Transactional
    public PackResult pack(String lpnBarcode, List<UUID> inventoryLevelIds,
                           List<UUID> allocationIds, List<String> barcodes) {
        UUID tenantId = TenantContext.requireTenantId();
        String barcode = normalizeBarcode(lpnBarcode);
        LicensePlate lpn = licensePlateRepository.findByTenantIdAndLpnBarcode(tenantId, barcode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LPN_NOT_FOUND", "License plate not found"));
        if ("DISPATCHED".equals(lpn.getStatus()) || "CLOSED".equals(lpn.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "LPN_NOT_OPEN",
                    "Cannot pack onto a " + lpn.getStatus() + " license plate");
        }

        Set<UUID> levelIds = new LinkedHashSet<>();
        if (inventoryLevelIds != null) {
            levelIds.addAll(inventoryLevelIds);
        }
        if (allocationIds != null) {
            for (UUID allocationId : allocationIds) {
                resolveLevelFromAllocation(tenantId, allocationId).ifPresent(levelIds::add);
            }
        }
        if (barcodes != null) {
            for (String scan : barcodes) {
                if (scan == null || scan.isBlank()) {
                    continue;
                }
                resolveLevelFromScan(tenantId, scan.trim()).ifPresent(levelIds::add);
            }
        }
        if (levelIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LEVELS_REQUIRED",
                    "Provide inventoryLevelIds, allocationIds, or barcodes to pack");
        }

        List<PackedLine> packed = new ArrayList<>();
        for (UUID levelId : levelIds) {
            InventoryLevel level = levelRepository.findById(levelId)
                    .filter(l -> tenantId.equals(l.getTenantId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEVEL_NOT_FOUND",
                            "Inventory level not found: " + levelId));
            if (level.getOnHand() == null || level.getOnHand().signum() <= 0) {
                continue;
            }
            if (lpn.getId().equals(level.getLpnId())) {
                continue;
            }

            if (lpn.getLocationId() == null) {
                lpn.setLocationId(level.getLocationId());
                licensePlateRepository.save(lpn);
            }

            UUID fromLoc = level.getLocationId();
            UUID toLoc = lpn.getLocationId() != null ? lpn.getLocationId() : fromLoc;
            BigDecimal qty = level.getOnHand();
            UUID sourceLpnId = level.getLpnId();

            inventoryService.consolidateOntoLpn(
                    level.getVariantId(),
                    fromLoc,
                    toLoc,
                    level.getLotId(),
                    sourceLpnId,
                    lpn.getId(),
                    qty);

            packed.add(new PackedLine(level.getId(), level.getVariantId(), level.getLotId(), qty, toLoc));
        }

        // Levels are async-flushed from ledger deltas after commit; within this TX read
        // levels + pending deltas (and fall back to packed.size()).
        entityManager.flush();
        entityManager.clear();
        int itemCount = countPackedItems(tenantId, lpn.getId());
        if (itemCount < packed.size()) {
            itemCount = packed.size();
        }
        return new PackResult(lpn.getId(), lpn.getLpnBarcode(), packed.size(), itemCount, packed);
    }

    @Transactional(readOnly = true)
    public LpnContents contents(String lpnBarcode) {
        UUID tenantId = TenantContext.requireTenantId();
        LicensePlate lpn = licensePlateRepository.findByTenantIdAndLpnBarcode(tenantId, normalizeBarcode(lpnBarcode))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LPN_NOT_FOUND", "License plate not found"));
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndLpnId(tenantId, lpn.getId()).stream()
                .filter(l -> l.getOnHand() != null && l.getOnHand().signum() > 0)
                .toList();
        BigDecimal totalQty = levels.stream()
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LpnContents(lpn.getId(), lpn.getLpnBarcode(), lpn.getStatus(), lpn.getLocationId(),
                levels.size(), totalQty, levels);
    }

    /**
     * Ship every positive on-hand level on the LPN and mark it {@code DISPATCHED}.
     */
    @Transactional
    public ShipLpnResult shipLpn(String lpnBarcode, UUID salesOrderId, UUID shipmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        LicensePlate lpn = licensePlateRepository.findByTenantIdAndLpnBarcode(tenantId, normalizeBarcode(lpnBarcode))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LPN_NOT_FOUND", "License plate not found"));
        if ("DISPATCHED".equals(lpn.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "LPN_ALREADY_DISPATCHED", "LPN already dispatched");
        }

        List<InventoryLevel> levels = levelRepository.findByTenantIdAndLpnId(tenantId, lpn.getId()).stream()
                .filter(l -> l.getOnHand() != null && l.getOnHand().signum() > 0)
                .toList();
        if (levels.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LPN_EMPTY",
                    "License plate has no stock to ship");
        }

        List<ShippedLpnLine> shipped = new ArrayList<>();
        for (InventoryLevel level : levels) {
            BigDecimal qty = level.getOnHand();
            inventoryService.shipLpnLevel(level, salesOrderId, shipmentId);
            shipped.add(new ShippedLpnLine(level.getVariantId(), level.getLotId(), qty, level.getLocationId()));
        }

        lpn.setStatus("DISPATCHED");
        licensePlateRepository.save(lpn);
        return new ShipLpnResult(lpn.getId(), lpn.getLpnBarcode(), shipped.size(), shipped);
    }

    private java.util.Optional<UUID> resolveLevelFromAllocation(UUID tenantId, UUID allocationId) {
        Allocation allocation = allocationRepository.findById(allocationId)
                .filter(a -> tenantId.equals(a.getTenantId()))
                .orElse(null);
        if (allocation == null) {
            return java.util.Optional.empty();
        }
        return levelRepository.findByTenantIdAndVariantId(tenantId, allocation.getVariantId()).stream()
                .filter(l -> allocation.getLocationId().equals(l.getLocationId()))
                .filter(l -> (allocation.getLotId() == null && l.getLotId() == null)
                        || (allocation.getLotId() != null && allocation.getLotId().equals(l.getLotId())))
                .filter(l -> l.getOnHand() != null && l.getOnHand().signum() > 0)
                .filter(l -> l.getLpnId() == null)
                .map(InventoryLevel::getId)
                .findFirst();
    }

    private java.util.Optional<UUID> resolveLevelFromScan(UUID tenantId, String scan) {
        // Allocation / level UUID
        try {
            UUID id = UUID.fromString(scan);
            if (levelRepository.findById(id).filter(l -> tenantId.equals(l.getTenantId())).isPresent()) {
                return java.util.Optional.of(id);
            }
            java.util.Optional<UUID> fromAlloc = resolveLevelFromAllocation(tenantId, id);
            if (fromAlloc.isPresent()) {
                return fromAlloc;
            }
            throw new ApiException(HttpStatus.NOT_FOUND, "LEVEL_NOT_FOUND",
                    "No packable inventory for id: " + scan);
        } catch (IllegalArgumentException ignored) {
            // not a UUID — treat as product barcode / SKU
        }

        ProductVariant variant = variantRepository.findByTenantIdAndBarcode(tenantId, scan)
                .or(() -> variantRepository.findByTenantIdAndSku(tenantId, scan))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND",
                        "No inventory for barcode: " + scan));
        return levelRepository.findByTenantIdAndVariantId(tenantId, variant.getId()).stream()
                .filter(l -> l.getOnHand() != null && l.getOnHand().signum() > 0)
                .filter(l -> l.getLpnId() == null)
                .sorted((a, b) -> b.getOnHand().compareTo(a.getOnHand()))
                .map(InventoryLevel::getId)
                .findFirst()
                .or(() -> {
                    throw new ApiException(HttpStatus.NOT_FOUND, "LEVEL_NOT_FOUND",
                            "No loose stock to pack for " + variant.getSku());
                });
    }

    private int countPackedItems(UUID tenantId, UUID lpnId) {
        long fromLevels = levelRepository.findByTenantIdAndLpnId(tenantId, lpnId).stream()
                .filter(l -> l.getOnHand() != null && l.getOnHand().signum() > 0)
                .count();
        if (fromLevels > 0) {
            return (int) fromLevels;
        }
        // Same-TX: LPN on_hand may still be only in inventory_level_deltas.
        return deltaFlushRepository.sumPendingOnHandForLpn(tenantId, lpnId).signum() > 0
                ? 1
                : 0;
    }

    private static String normalizeBarcode(String lpnBarcode) {
        return lpnBarcode == null ? "" : lpnBarcode.trim().toUpperCase();
    }

    private static String buildLpnZpl(String lpnBarcode) {
        String safe = sanitizeZpl(lpnBarcode);
        return """
                ^XA
                ^FO40,40^A0N,36,36^FDLICENSE PLATE^FS
                ^FO40,90^A0N,48,48^FD%s^FS
                ^FO40,160^BCN,100,Y,N,N^FD%s^FS
                ^FO40,300^A0N,22,22^FDPallet / bulk move^FS
                ^XZ
                """.formatted(safe, safe).replace("\r", "").strip() + "\n";
    }

    private static String sanitizeZpl(String value) {
        return value.replace("^", "").replace("~", "");
    }

    public record MintedLpn(UUID id, String lpnBarcode, UUID locationId, String status, String zpl) {
    }

    public record PackResult(
            UUID lpnId,
            String lpnBarcode,
            int linesPacked,
            int itemCount,
            List<PackedLine> lines
    ) {
    }

    public record PackedLine(
            UUID inventoryLevelId,
            UUID variantId,
            UUID lotId,
            BigDecimal quantity,
            UUID locationId
    ) {
    }

    public record LpnContents(
            UUID lpnId,
            String lpnBarcode,
            String status,
            UUID locationId,
            int lineCount,
            BigDecimal totalQuantity,
            List<InventoryLevel> levels
    ) {
    }

    public record ShipLpnResult(
            UUID lpnId,
            String lpnBarcode,
            int linesShipped,
            List<ShippedLpnLine> lines
    ) {
    }

    public record ShippedLpnLine(
            UUID variantId,
            UUID lotId,
            BigDecimal quantity,
            UUID locationId
    ) {
    }
}
