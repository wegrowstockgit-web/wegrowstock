package com.invsys.modules.inventory.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.inventory.domain.LicensePlate;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Lot;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.core.integration.OutboxService;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.inventory.repository.InventoryLevelDeltaFlushRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.inventory.repository.LicensePlateRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.LotRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.SerialNumberRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.invsys.domain.Tenant;
import com.invsys.service.CostingService;
import com.invsys.service.LedgerCommittedEvent;
import com.invsys.service.PutawayValidationService;
import com.invsys.service.SerialNumberService;

@Service
public class InventoryService {

    private final InventoryLedgerRepository ledgerRepository;
    private final InventoryLevelRepository levelRepository;
    private final TenantSettingsRepository settingsRepository;
    private final AllocationRepository allocationRepository;
    private final ProductVariantRepository variantRepository;
    private final LotRepository lotRepository;
    private final CostingService costingService;
    private final OutboxService outboxService;
    private final SerialNumberService serialNumberService;
    private final SerialNumberRepository serialNumberRepository;
    private final CycleCountService cycleCountService;
    private final LicensePlateRepository licensePlateRepository;
    private final LocationRepository locationRepository;
    private final InventoryLevelDeltaFlushRepository deltaFlushRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PutawayValidationService putawayValidationService;

    public InventoryService(InventoryLedgerRepository ledgerRepository,
                            InventoryLevelRepository levelRepository,
                            TenantSettingsRepository settingsRepository,
                            AllocationRepository allocationRepository,
                            ProductVariantRepository variantRepository,
                            LotRepository lotRepository,
                            CostingService costingService,
                            OutboxService outboxService,
                            SerialNumberService serialNumberService,
                            SerialNumberRepository serialNumberRepository,
                            @org.springframework.context.annotation.Lazy CycleCountService cycleCountService,
                            LicensePlateRepository licensePlateRepository,
                            LocationRepository locationRepository,
                            InventoryLevelDeltaFlushRepository deltaFlushRepository,
                            ApplicationEventPublisher eventPublisher,
                            PutawayValidationService putawayValidationService) {
        this.ledgerRepository = ledgerRepository;
        this.levelRepository = levelRepository;
        this.settingsRepository = settingsRepository;
        this.allocationRepository = allocationRepository;
        this.variantRepository = variantRepository;
        this.lotRepository = lotRepository;
        this.costingService = costingService;
        this.outboxService = outboxService;
        this.serialNumberService = serialNumberService;
        this.serialNumberRepository = serialNumberRepository;
        this.cycleCountService = cycleCountService;
        this.licensePlateRepository = licensePlateRepository;
        this.locationRepository = locationRepository;
        this.deltaFlushRepository = deltaFlushRepository;
        this.eventPublisher = eventPublisher;
        this.putawayValidationService = putawayValidationService;
    }

    /**
     * Graceful lot resolution: when {@code is_lot_tracked} is false and no lotId is supplied,
     * keep {@code lot_id = null} and sink vendor lot strings into ledger metadata
     * ({@code vendor_lot_captured}). Explicit allocation lotIds are preserved for level targeting.
     */
    public ResolvedLot resolveLot(ProductVariant variant,
                                  UUID lotId,
                                  String lotNumber,
                                  Map<String, Object> clientMetadata) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (clientMetadata != null) {
            meta.putAll(clientMetadata);
        }
        // When a concrete lotId is supplied (e.g. from an allocation), keep it for level targeting
        // even if the variant is not lot-tracked — otherwise pick/ship ADJUSTs write lot_id=NULL
        // and fail validateOnHand against seeded lot-keyed inventory_levels rows.
        if (lotId != null) {
            if (!variant.isLotTracked()) {
                String captured = firstNonBlank(lotNumber, lookupLotNumber(lotId));
                if (captured != null) {
                    meta.putIfAbsent("vendor_lot_captured", captured);
                }
                return new ResolvedLot(lotId, meta, captured != null);
            }
            return new ResolvedLot(lotId, meta, false);
        }
        if (!variant.isLotTracked()) {
            String captured = firstNonBlank(lotNumber, null);
            if (captured != null) {
                meta.putIfAbsent("vendor_lot_captured", captured);
            }
            return new ResolvedLot(null, meta, captured != null);
        }
        if (lotNumber != null && !lotNumber.isBlank()) {
            Instant expiresAt = parseLotExpiry(meta.get("lot_expires_at"));
            return new ResolvedLot(findOrCreateLot(variant.getId(), lotNumber.trim(), expiresAt), meta, false);
        }
        return new ResolvedLot(null, meta, false);
    }

    public record ResolvedLot(UUID lotId, Map<String, Object> metadata, boolean lotLoggedNotTracked) {
    }

    @Transactional
    public InventoryLedger receive(UUID variantId, UUID locationId, UUID lotId, BigDecimal quantity,
                                   String referenceType, UUID referenceId) {
        return receive(variantId, locationId, lotId, quantity, referenceType, referenceId, null, null);
    }

    @Transactional
    public InventoryLedger receive(UUID variantId, UUID locationId, UUID lotId, BigDecimal quantity,
                                   String referenceType, UUID referenceId, BigDecimal unitCost) {
        return receive(variantId, locationId, lotId, quantity, referenceType, referenceId, unitCost, null);
    }

    @Transactional
    public InventoryLedger receive(UUID variantId, UUID locationId, UUID lotId, BigDecimal quantity,
                                   String referenceType, UUID referenceId, BigDecimal unitCost,
                                   String serialCode) {
        return receive(variantId, locationId, lotId, null, quantity, referenceType, referenceId,
                unitCost, serialCode, null);
    }

    @Transactional
    public InventoryLedger receive(UUID variantId, UUID locationId, UUID lotId, String lotNumber,
                                   BigDecimal quantity, String referenceType, UUID referenceId,
                                   BigDecimal unitCost, String serialCode, Map<String, Object> metadata) {
        return receive(variantId, locationId, lotId, lotNumber, quantity, null, referenceType, referenceId,
                unitCost, serialCode, metadata);
    }

    /**
     * Opening-balance receive for legacy ERP cutover ({@code reason_code = INITIAL_MIGRATION}).
     */
    @Transactional
    public InventoryLedger receiveInitialMigration(UUID variantId, UUID locationId,
                                                   BigDecimal quantity, BigDecimal unitCost) {
        return receiveInitialMigration(variantId, locationId, quantity, unitCost, null, null);
    }

    @Transactional
    public InventoryLedger receiveInitialMigration(UUID variantId, UUID locationId,
                                                   BigDecimal quantity, BigDecimal unitCost,
                                                   String lotNumber, Map<String, Object> metadata) {
        return receive(variantId, locationId, null, lotNumber, quantity, "INITIAL_MIGRATION",
                "LEGACY_ERP_MIGRATION", null, unitCost, null, metadata);
    }

    @Transactional
    public InventoryLedger receive(UUID variantId, UUID locationId, UUID lotId, String lotNumber,
                                   BigDecimal quantity, String reasonCode, String referenceType, UUID referenceId,
                                   BigDecimal unitCost, String serialCode, Map<String, Object> metadata) {
        putawayValidationService.validatePutaway(variantId, locationId, quantity, extractManagerOverridePin(metadata));
        ProductVariant variant = serialNumberService.requireVariant(variantId);
        serialNumberService.validateSerializedQuantity(variant, quantity);
        UUID serialId = null;
        if (variant.isTrackSerials()) {
            if (serialCode == null || serialCode.isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SERIAL_REQUIRED",
                        "Serial number is required for serialized variants");
            }
            serialId = serialNumberService.receiveSerial(variantId, serialCode.trim()).getId();
        }
        ResolvedLot resolved = resolveLot(variant, lotId, lotNumber, metadata);
        costingService.applyReceiveCost(variantId, quantity.abs(), unitCost);
        InventoryLedger entry = appendMovement("RECEIVE", variantId, locationId, resolved.lotId(), quantity.abs(),
                reasonCode, referenceType, referenceId, null, unitCost, serialId, resolved.metadata());
        emitIntegrationEvents(entry, variantId);
        return entry;
    }

    /**
     * Receive stock into a location without increasing ATP/available.
     * Posts a RECEIVE ledger (on_hand++) and an ACTIVE allocation hold (allocated++)
     * so available = on_hand - allocated stays unchanged.
     */
    @Transactional
    public InventoryLedger quarantineReceive(UUID variantId, UUID locationId, UUID lotId, BigDecimal quantity,
                                             String referenceType, UUID referenceId, UUID salesOrderLineId) {
        ProductVariant variant = serialNumberService.requireVariant(variantId);
        serialNumberService.validateSerializedQuantity(variant, quantity);
        BigDecimal qty = quantity.abs();
        costingService.applyReceiveCost(variantId, qty, null);
        ResolvedLot resolved = resolveLot(variant, lotId, null, null);
        InventoryLedger entry = appendMovement("RECEIVE", variantId, locationId, resolved.lotId(), qty,
                "RMA_QUARANTINE", referenceType, referenceId, null, null, null, resolved.metadata());

        Allocation hold = new Allocation();
        hold.setTenantId(TenantContext.requireTenantId());
        hold.setSalesOrderLineId(salesOrderLineId);
        hold.setVariantId(variantId);
        hold.setLocationId(locationId);
        hold.setLotId(resolved.lotId());
        hold.setQuantity(qty);
        hold.setStatus("ACTIVE");
        allocationRepository.save(hold);

        emitIntegrationEvents(entry, variantId);
        return entry;
    }

    /**
     * Release quarantine hold into ATP (RESTOCK) or scrap from quarantine (SCRAP).
     * ACTIVE allocation holds keep available flat after quarantineReceive; releasing them
     * restores ATP, while scrapping consumes the hold and adjusts on-hand down.
     */
    @Transactional
    public void releaseQuarantineHold(UUID salesOrderLineId, UUID variantId, UUID locationId, UUID lotId,
                                      BigDecimal quantity, String disposition) {
        BigDecimal remaining = quantity.abs();
        List<Allocation> holds = allocationRepository
                .findBySalesOrderLineIdAndStatus(salesOrderLineId, "ACTIVE").stream()
                .filter(a -> a.getVariantId().equals(variantId))
                .filter(a -> a.getLocationId().equals(locationId))
                .filter(a -> (lotId == null && a.getLotId() == null) || (lotId != null && lotId.equals(a.getLotId())))
                .toList();

        for (Allocation hold : holds) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = hold.getQuantity().min(remaining);
            if (take.compareTo(hold.getQuantity()) == 0) {
                hold.setStatus("SCRAP".equals(disposition) ? "CONSUMED" : "RELEASED");
                allocationRepository.save(hold);
            } else {
                hold.setQuantity(hold.getQuantity().subtract(take));
                allocationRepository.save(hold);
            }
            if ("SCRAP".equals(disposition)) {
                appendMovement("ADJUST", variantId, locationId, lotId, take.negate(),
                        "RMA_SCRAP", "RETURN", null, null, null, null);
            }
            remaining = remaining.subtract(take);
        }

        if (remaining.signum() > 0 && "SCRAP".equals(disposition)) {
            appendMovement("ADJUST", variantId, locationId, lotId, remaining.negate(),
                    "RMA_SCRAP", "RETURN", null, null, null, null);
        }
    }

    @Transactional
    public InventoryLedger adjust(UUID variantId, UUID locationId, UUID lotId, BigDecimal delta, String reasonCode) {
        return adjust(variantId, locationId, lotId, delta, reasonCode, null);
    }

    @Transactional
    public InventoryLedger adjust(UUID variantId, UUID locationId, UUID lotId, BigDecimal delta,
                                  String reasonCode, String serialCode) {
        return adjust(variantId, locationId, lotId, null, delta, reasonCode, serialCode, null);
    }

    @Transactional
    public InventoryLedger adjust(UUID variantId, UUID locationId, UUID lotId, String lotNumber,
                                  BigDecimal delta, String reasonCode, String serialCode,
                                  Map<String, Object> metadata) {
        ProductVariant variant = serialNumberService.requireVariant(variantId);
        serialNumberService.validateSerializedQuantity(variant, delta);
        UUID serialId = null;
        if (variant.isTrackSerials()) {
            if (serialCode == null || serialCode.isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SERIAL_REQUIRED",
                        "Serial number is required for serialized variants");
            }
            if (delta.signum() > 0) {
                serialId = serialNumberService.receiveSerial(variantId, serialCode.trim()).getId();
            } else {
                serialId = serialNumberService.consumeSerial(variantId, serialCode.trim()).getId();
            }
        }
        ResolvedLot resolved = resolveLot(variant, lotId, lotNumber, metadata);
        validateNegative(delta, variantId, locationId, resolved.lotId());
        return appendMovement("ADJUST", variantId, locationId, resolved.lotId(), delta, reasonCode,
                null, null, null, null, serialId, resolved.metadata());
    }

    /**
     * Quantity-neutral cost correction (landed freight). Does not change on_hand (delta = 0).
     */
    @Transactional
    public InventoryLedger appendCostAdjustment(UUID variantId,
                                                UUID locationId,
                                                BigDecimal unitCost,
                                                String reasonCode,
                                                String referenceType,
                                                UUID referenceId) {
        return appendCostAdjustment(variantId, locationId, unitCost, reasonCode, referenceType, referenceId, null);
    }

    @Transactional
    public InventoryLedger appendCostAdjustment(UUID variantId,
                                                UUID locationId,
                                                BigDecimal unitCost,
                                                String reasonCode,
                                                String referenceType,
                                                UUID referenceId,
                                                BigDecimal landedCostComponent) {
        InventoryLedger entry = new InventoryLedger();
        entry.setTenantId(TenantContext.requireTenantId());
        entry.setVariantId(variantId);
        entry.setLocationId(locationId);
        entry.setMovementType("ADJUST");
        entry.setQuantityDelta(BigDecimal.ZERO);
        entry.setReasonCode(reasonCode);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setUnitCost(unitCost);
        entry.setLandedCostComponent(landedCostComponent != null ? landedCostComponent : BigDecimal.ZERO);
        entry.setCreatedBy(TenantContext.getUserId().orElse(null));
        InventoryLedger saved = ledgerRepository.save(entry);
        cycleCountService.evaluateLocationVelocity(locationId);
        return saved;
    }

    @Transactional
    public UUID transfer(UUID variantId, UUID fromLocationId, UUID toLocationId, UUID lotId, BigDecimal quantity) {
        return transfer(variantId, fromLocationId, toLocationId, lotId, quantity, null);
    }

    @Transactional
    public UUID transfer(UUID variantId, UUID fromLocationId, UUID toLocationId, UUID lotId, BigDecimal quantity,
                         String managerOverridePin) {
        putawayValidationService.validatePutaway(variantId, toLocationId, quantity, managerOverridePin);
        validateNegative(quantity.negate(), variantId, fromLocationId, lotId);
        UUID groupId = UUID.randomUUID();
        appendMovement("TRANSFER_OUT", variantId, fromLocationId, lotId, null, quantity.negate(),
                null, null, null, groupId, null, null, null);
        appendMovement("TRANSFER_IN", variantId, toLocationId, lotId, null, quantity.abs(),
                null, null, null, groupId, null, null, null);
        return groupId;
    }

    private static String extractManagerOverridePin(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object pin = metadata.get("managerOverridePin");
        return pin == null ? null : String.valueOf(pin);
    }

    /**
     * Resolve a scanned bin code or full location path to a location id.
     */
    @Transactional(readOnly = true)
    public UUID resolveLocationId(String barcodeOrPath) {
        UUID tenantId = TenantContext.requireTenantId();
        String key = barcodeOrPath == null ? "" : barcodeOrPath.trim();
        if (key.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_REQUIRED", "Location barcode required");
        }
        return locationRepository.findByTenantIdAndCode(tenantId, key)
                .or(() -> locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                        .filter(l -> key.equalsIgnoreCase(l.getPath())
                                || key.equalsIgnoreCase(l.getCode())
                                || (l.getPath() != null && l.getPath().toUpperCase().endsWith("/" + key.toUpperCase())))
                        .findFirst())
                .map(Location::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND",
                        "Location not found for barcode: " + key));
    }

    @Transactional
    public LicensePlate createLicensePlate(String lpnBarcode, UUID locationId) {
        UUID tenantId = TenantContext.requireTenantId();
        String barcode = lpnBarcode == null || lpnBarcode.isBlank()
                ? "LPN-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                : lpnBarcode.trim().toUpperCase();
        if (licensePlateRepository.findByTenantIdAndLpnBarcode(tenantId, barcode).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "LPN_EXISTS", "LPN barcode already exists");
        }
        Location location = locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Location not found"));
        LicensePlate lpn = new LicensePlate();
        lpn.setTenantId(tenantId);
        lpn.setLpnBarcode(barcode);
        lpn.setLocationId(location.getId());
        lpn.setStatus("OPEN");
        return licensePlateRepository.save(lpn);
    }

    /**
     * Receive stock onto an LPN (bulk pallet / tote) at the LPN's current location.
     */
    @Transactional
    public InventoryLedger receiveOntoLpn(UUID variantId, UUID lpnId, BigDecimal quantity) {
        return receiveOntoLpn(variantId, lpnId, quantity, null);
    }

    @Transactional
    public InventoryLedger receiveOntoLpn(UUID variantId, UUID lpnId, BigDecimal quantity, String managerOverridePin) {
        UUID tenantId = TenantContext.requireTenantId();
        LicensePlate lpn = licensePlateRepository.findByIdAndTenantId(lpnId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LPN_NOT_FOUND", "License plate not found"));
        if (lpn.getLocationId() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LPN_NO_LOCATION",
                    "License plate has no location");
        }
        putawayValidationService.validatePutaway(variantId, lpn.getLocationId(), quantity, managerOverridePin);
        serialNumberService.requireVariant(variantId);
        return appendMovement("RECEIVE", variantId, lpn.getLocationId(), null, lpn.getId(),
                quantity.abs(), "LPN_RECEIVE", null, null, null, null, null, null);
    }

    /**
     * Consolidate stock from a source LPN (nullable = loose floor) onto a target LPN.
     * Posts TRANSFER_OUT / TRANSFER_IN with reason {@code LPN_CONSOLIDATION}.
     */
    @Transactional
    public void consolidateOntoLpn(UUID variantId,
                                   UUID fromLocationId,
                                   UUID toLocationId,
                                   UUID lotId,
                                   UUID sourceLpnId,
                                   UUID targetLpnId,
                                   BigDecimal quantity) {
        BigDecimal qty = quantity.abs();
        validateOnHand(qty.negate(), variantId, fromLocationId, lotId);
        UUID groupId = UUID.randomUUID();
        appendMovement("TRANSFER_OUT", variantId, fromLocationId, lotId, sourceLpnId,
                qty.negate(), "LPN_CONSOLIDATION", "LICENSE_PLATE", targetLpnId, groupId, null, null, null);
        appendMovement("TRANSFER_IN", variantId, toLocationId, lotId, targetLpnId,
                qty, "LPN_CONSOLIDATION", "LICENSE_PLATE", targetLpnId, groupId, null, null, null);
    }

    /**
     * Ship an LPN-scoped inventory level (bulk pallet outbound).
     */
    @Transactional
    public InventoryLedger shipLpnLevel(InventoryLevel level, UUID salesOrderId, UUID shipmentId) {
        BigDecimal qty = level.getOnHand().abs();
        validateOnHand(qty.negate(), level.getVariantId(), level.getLocationId(), level.getLotId());
        BigDecimal unitCost = costingService.snapshotShipCost(level.getVariantId());
        InventoryLedger entry = appendMovement("SHIP", level.getVariantId(), level.getLocationId(),
                level.getLotId(), level.getLpnId(), qty.negate(), "LPN_SHIP",
                shipmentId != null ? "SHIPMENT" : "SALES_ORDER",
                shipmentId != null ? shipmentId : salesOrderId,
                null, unitCost, null, null);
        emitIntegrationEvents(entry, level.getVariantId());
        return entry;
    }

    /**
     * Return-to-vendor outbound: append-only SHIP against on-hand (no sales allocation).
     */
    @Transactional
    public InventoryLedger shipVendorReturn(UUID variantId, UUID locationId, UUID lotId,
                                            BigDecimal quantity, UUID rtvOrderId) {
        BigDecimal qty = quantity.abs();
        validateOnHand(qty.negate(), variantId, locationId, lotId);
        BigDecimal unitCost = costingService.snapshotShipCost(variantId);
        InventoryLedger entry = appendMovement("SHIP", variantId, locationId, lotId, null,
                qty.negate(), "RTV", "RTV_ORDER", rtvOrderId, null, unitCost, null, null);
        emitIntegrationEvents(entry, variantId);
        return entry;
    }

    /**
     * Bulk-move every inventory level tied to an LPN to {@code destinationLocationId}
     * in one transaction (TRANSFER_OUT / TRANSFER_IN per SKU line + LPN header update).
     */
    @Transactional
    public MoveLpnResult moveLpn(UUID tenantId, String lpnBarcode, UUID destinationLocationId) {
        UUID contextTenant = TenantContext.requireTenantId();
        if (!contextTenant.equals(tenantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_MISMATCH", "Tenant mismatch");
        }
        String barcode = lpnBarcode == null ? "" : lpnBarcode.trim().toUpperCase();
        LicensePlate lpn = licensePlateRepository.findByTenantIdAndLpnBarcode(tenantId, barcode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LPN_NOT_FOUND", "License plate not found"));
        Location destination = locationRepository.findById(destinationLocationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Destination not found"));
        if (destination.getId().equals(lpn.getLocationId())) {
            return new MoveLpnResult(lpn.getId(), lpn.getLpnBarcode(), destination.getId(), 0, List.of());
        }

        List<InventoryLevel> levels = levelRepository.findByTenantIdAndLpnId(tenantId, lpn.getId()).stream()
                .filter(l -> l.getOnHand() != null && l.getOnHand().signum() > 0)
                .toList();
        if (levels.isEmpty()) {
            lpn.setLocationId(destination.getId());
            lpn.setStatus("OPEN");
            licensePlateRepository.save(lpn);
            return new MoveLpnResult(lpn.getId(), lpn.getLpnBarcode(), destination.getId(), 0, List.of());
        }

        UUID groupId = UUID.randomUUID();
        List<MovedLpnLine> moved = new ArrayList<>();
        lpn.setStatus("IN_TRANSIT");
        licensePlateRepository.save(lpn);

        for (InventoryLevel level : levels) {
            BigDecimal qty = level.getOnHand();
            UUID fromLoc = level.getLocationId();
            appendMovement("TRANSFER_OUT", level.getVariantId(), fromLoc, level.getLotId(), lpn.getId(),
                    qty.negate(), "LPN_MOVE", "LICENSE_PLATE", lpn.getId(), groupId, null, null, null);
            appendMovement("TRANSFER_IN", level.getVariantId(), destination.getId(), level.getLotId(), lpn.getId(),
                    qty, "LPN_MOVE", "LICENSE_PLATE", lpn.getId(), groupId, null, null, null);
            moved.add(new MovedLpnLine(level.getVariantId(), level.getLotId(), qty, fromLoc, destination.getId()));
        }

        lpn.setLocationId(destination.getId());
        lpn.setStatus("OPEN");
        licensePlateRepository.save(lpn);
        return new MoveLpnResult(lpn.getId(), lpn.getLpnBarcode(), destination.getId(), moved.size(), moved);
    }

    public record MoveLpnResult(
            UUID lpnId,
            String lpnBarcode,
            UUID destinationLocationId,
            int linesMoved,
            List<MovedLpnLine> lines
    ) {
    }

    public record MovedLpnLine(
            UUID variantId,
            UUID lotId,
            BigDecimal quantity,
            UUID fromLocationId,
            UUID toLocationId
    ) {
    }

    /**
     * Consume stock for internal requisition issue. Appends ADJUST only (ledger is immutable).
     */
    @Transactional
    public InventoryLedger consumeInternal(UUID variantId, UUID locationId, UUID lotId, BigDecimal quantity,
                                           UUID referenceId) {
        BigDecimal qty = quantity.abs();
        validateNegative(qty.negate(), variantId, locationId, lotId);
        BigDecimal unitCost = costingService.snapshotShipCost(variantId);
        return appendMovement("ADJUST", variantId, locationId, lotId, qty.negate(),
                "INTERNAL_CONSUMPTION", "INTERNAL_REQUISITION_LINE", referenceId, null, unitCost, null);
    }

    /**
     * Consume stock from a technician van with a free-form service reason.
     */
    @Transactional
    public InventoryLedger consumeService(UUID variantId, UUID locationId, UUID lotId, BigDecimal quantity,
                                          String reasonCode) {
        BigDecimal qty = quantity.abs();
        validateNegative(qty.negate(), variantId, locationId, lotId);
        BigDecimal unitCost = costingService.snapshotShipCost(variantId);
        String reason = reasonCode != null && !reasonCode.isBlank() ? reasonCode : "SERVICE_CONSUMPTION";
        return appendMovement("ADJUST", variantId, locationId, lotId, qty.negate(),
                reason, null, null, null, unitCost, null);
    }

    /**
     * Append-only error correction: posts a compensating ADJUST that negates the original
     * quantity_delta. Inventory levels are corrected by the AFTER-INSERT ledger trigger.
     */
    @Transactional
    public InventoryLedger reverseLedgerEntry(UUID ledgerId) {
        UUID tenantId = TenantContext.requireTenantId();
        InventoryLedger original = ledgerRepository.findById(ledgerId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEDGER_NOT_FOUND",
                        "Ledger entry not found"));

        if ("ERROR_CORRECTION".equals(original.getReasonCode()) || original.getReversalOfLedgerId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_REVERSE_REVERSAL",
                    "Compensating entries cannot be reversed");
        }
        if (ledgerRepository.existsByTenantIdAndReversalOfLedgerId(tenantId, original.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REVERSED",
                    "This ledger entry has already been reversed");
        }

        BigDecimal reverseDelta = original.getQuantityDelta().negate();
        validateNegative(reverseDelta, original.getVariantId(), original.getLocationId(), original.getLotId());

        InventoryLedger entry = new InventoryLedger();
        entry.setTenantId(tenantId);
        entry.setVariantId(original.getVariantId());
        entry.setLocationId(original.getLocationId());
        entry.setLotId(original.getLotId());
        entry.setMovementType("ADJUST");
        entry.setQuantityDelta(reverseDelta);
        entry.setUnitCost(original.getUnitCost());
        entry.setReasonCode("ERROR_CORRECTION");
        entry.setReversalOfLedgerId(original.getId());
        entry.setReferenceType(original.getReferenceType());
        entry.setReferenceId(original.getReferenceId());
        entry.setSerialNumberId(original.getSerialNumberId());
        entry.setLandedCostComponent(
                original.getLandedCostComponent() != null
                        ? original.getLandedCostComponent().negate()
                        : BigDecimal.ZERO);
        entry.setMetadata(Map.of("reversed_ledger_id", original.getId().toString()));
        entry.setCreatedBy(TenantContext.getUserId().orElse(null));
        InventoryLedger saved = ledgerRepository.save(entry);
        cycleCountService.evaluateLocationVelocity(original.getLocationId());
        emitIntegrationEvents(saved, original.getVariantId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InventoryLedger> listRecentLedger(int limit) {
        return listRecentLedger(limit, null);
    }

    @Transactional(readOnly = true)
    public List<InventoryLedger> listRecentLedger(int limit, UUID variantId) {
        UUID tenantId = TenantContext.requireTenantId();
        int capped = Math.min(Math.max(limit, 1), 100);
        List<InventoryLedger> rows = variantId != null
                ? ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, variantId)
                : ledgerRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);
        if (rows.size() <= capped) {
            return rows;
        }
        return rows.subList(0, capped);
    }

    @Transactional
    public InventoryLedger ship(Allocation allocation, BigDecimal quantity) {
        return ship(allocation, quantity, null);
    }

    @Transactional
    public InventoryLedger ship(Allocation allocation, BigDecimal quantity, String serialCode) {
        ProductVariant variant = serialNumberService.requireVariant(allocation.getVariantId());
        serialNumberService.validateSerializedQuantity(variant, quantity.negate());
        UUID serialId = allocation.getSerialNumberId();
        if (variant.isTrackSerials()) {
            if (serialCode != null && !serialCode.isBlank()) {
                serialId = serialNumberService.consumeSerial(allocation.getVariantId(), serialCode.trim()).getId();
            } else if (serialId != null) {
                serialNumberService.assertAssignable(serialId);
                serialNumberRepository.findById(serialId).ifPresent(s -> {
                    s.setStatus("SHIPPED");
                    serialNumberRepository.save(s);
                });
            } else {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SERIAL_REQUIRED",
                        "Serial number is required for serialized variants");
            }
            allocation.setSerialNumberId(serialId);
        }
        ResolvedLot resolved = resolveLot(variant, allocation.getLotId(), null, null);
        // Reserved stock has available=0; ship consumes against on-hand at the allocation bin.
        validateOnHand(quantity.negate(), allocation.getVariantId(), allocation.getLocationId(), resolved.lotId());
        allocation.setStatus("CONSUMED");
        allocationRepository.saveAndFlush(allocation);
        BigDecimal unitCost = costingService.snapshotShipCost(allocation.getVariantId());
        InventoryLedger entry = appendMovement("SHIP", allocation.getVariantId(), allocation.getLocationId(),
                resolved.lotId(), quantity.negate(), null, "SALES_ORDER_LINE", allocation.getSalesOrderLineId(),
                null, unitCost, serialId, resolved.metadata());
        emitIntegrationEvents(entry, allocation.getVariantId());
        return entry;
    }

    /**
     * Floor pick ADJUST against an allocation hold. Validates {@code on_hand} (not available)
     * because reserved qty already has available=0.
     */
    @Transactional
    public InventoryLedger adjustReserved(UUID variantId, UUID locationId, UUID lotId, String lotNumber,
                                          BigDecimal delta, String reasonCode, String serialCode,
                                          Map<String, Object> metadata) {
        ProductVariant variant = serialNumberService.requireVariant(variantId);
        serialNumberService.validateSerializedQuantity(variant, delta);
        UUID serialId = null;
        if (variant.isTrackSerials()) {
            if (serialCode == null || serialCode.isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SERIAL_REQUIRED",
                        "Serial number is required for serialized variants");
            }
            if (delta.signum() > 0) {
                serialId = serialNumberService.receiveSerial(variantId, serialCode.trim()).getId();
            } else {
                serialId = serialNumberService.consumeSerial(variantId, serialCode.trim()).getId();
            }
        }
        ResolvedLot resolved = resolveLot(variant, lotId, lotNumber, metadata);
        validateOnHand(delta, variantId, locationId, resolved.lotId());
        return appendMovement("ADJUST", variantId, locationId, resolved.lotId(), delta, reasonCode,
                null, null, null, null, serialId, resolved.metadata());
    }

    private InventoryLedger appendMovement(String type, UUID variantId, UUID locationId, UUID lotId,
                                           BigDecimal delta, String reasonCode, String refType, UUID refId,
                                           UUID transferGroupId, BigDecimal unitCost, UUID serialNumberId) {
        return appendMovement(type, variantId, locationId, lotId, null, delta, reasonCode, refType, refId,
                transferGroupId, unitCost, serialNumberId, null);
    }

    private InventoryLedger appendMovement(String type, UUID variantId, UUID locationId, UUID lotId,
                                           BigDecimal delta, String reasonCode, String refType, UUID refId,
                                           UUID transferGroupId, BigDecimal unitCost, UUID serialNumberId,
                                           Map<String, Object> metadata) {
        return appendMovement(type, variantId, locationId, lotId, null, delta, reasonCode, refType, refId,
                transferGroupId, unitCost, serialNumberId, metadata);
    }

    private InventoryLedger appendMovement(String type, UUID variantId, UUID locationId, UUID lotId, UUID lpnId,
                                           BigDecimal delta, String reasonCode, String refType, UUID refId,
                                           UUID transferGroupId, BigDecimal unitCost, UUID serialNumberId,
                                           Map<String, Object> metadata) {
        InventoryLedger entry = new InventoryLedger();
        entry.setTenantId(TenantContext.requireTenantId());
        entry.setVariantId(variantId);
        entry.setLocationId(locationId);
        entry.setLotId(lotId);
        entry.setLpnId(lpnId);
        entry.setMovementType(type);
        entry.setQuantityDelta(delta);
        entry.setReasonCode(reasonCode);
        entry.setReferenceType(refType);
        entry.setReferenceId(refId);
        entry.setTransferGroupId(transferGroupId);
        entry.setUnitCost(unitCost);
        entry.setSerialNumberId(serialNumberId);
        entry.setMetadata(metadata != null ? metadata : Map.of());
        entry.setCreatedBy(TenantContext.getUserId().orElse(null));
        InventoryLedger saved = ledgerRepository.save(entry);
        eventPublisher.publishEvent(new LedgerCommittedEvent(saved.getTenantId(), saved.getId()));
        if ("ADJUST".equals(type) || "TRANSFER_OUT".equals(type) || "TRANSFER_IN".equals(type) || "SHIP".equals(type)) {
            cycleCountService.evaluateLocationVelocity(locationId);
        }
        return saved;
    }

    private UUID findOrCreateLot(UUID variantId, String lotNumber, Instant expiresAt) {
        UUID tenantId = TenantContext.requireTenantId();
        return lotRepository.findByTenantIdAndVariantIdAndLotNumber(tenantId, variantId, lotNumber)
                .map(existing -> {
                    if (expiresAt != null && existing.getExpiresAt() == null) {
                        existing.setExpiresAt(expiresAt);
                        return lotRepository.save(existing).getId();
                    }
                    return existing.getId();
                })
                .orElseGet(() -> {
                    Lot lot = new Lot();
                    lot.setTenantId(tenantId);
                    lot.setVariantId(variantId);
                    lot.setLotNumber(lotNumber);
                    lot.setReceivedAt(Instant.now());
                    if (expiresAt != null) {
                        lot.setExpiresAt(expiresAt);
                    }
                    return lotRepository.save(lot).getId();
                });
    }

    private static Instant parseLotExpiry(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            if (text.length() == 10) {
                return java.time.LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(text);
        } catch (Exception ignored) {
            try {
                return java.time.LocalDate.parse(text, java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"))
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String lookupLotNumber(UUID lotId) {
        if (lotId == null) {
            return null;
        }
        return lotRepository.findById(lotId).map(Lot::getLotNumber).orElse(null);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private void emitIntegrationEvents(InventoryLedger entry, UUID variantId) {
        if (!shouldSyncVariant(variantId)) {
            return;
        }
        Map<String, Object> ledgerPayload = new LinkedHashMap<>();
        ledgerPayload.put("movementType", entry.getMovementType());
        ledgerPayload.put("variantId", variantId.toString());
        ledgerPayload.put("quantityDelta", entry.getQuantityDelta());
        if (entry.getUnitCost() != null) {
            ledgerPayload.put("unitCost", entry.getUnitCost());
        }
        outboxService.append("INVENTORY_LEDGER", entry.getId(), "LEDGER_ENTRY_ARRIVED", ledgerPayload);

        Map<String, Object> stockPayload = new LinkedHashMap<>();
        stockPayload.put("variantId", variantId.toString());
        stockPayload.put("movementType", entry.getMovementType());
        outboxService.append("PRODUCT_VARIANT", variantId, "STOCK_LEVEL_CHANGED", stockPayload);
    }

    private boolean shouldSyncVariant(UUID variantId) {
        return variantRepository.findById(variantId)
                .map(ProductVariant::isExternalSyncEnabled)
                .orElse(true);
    }

    private void validateNegative(BigDecimal delta, UUID variantId, UUID locationId, UUID lotId) {
        if (delta.signum() >= 0) {
            return;
        }
        boolean allowNegative = settingsRepository.findByTenantId(TenantContext.requireTenantId())
                .map(TenantSettings::getSettings)
                .map(s -> Boolean.TRUE.equals(s.get("allow_negative_inventory")))
                .orElse(false);
        if (allowNegative) {
            return;
        }
        UUID tenantId = TenantContext.requireTenantId();
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(tenantId, variantId);
        BigDecimal available = levels.stream()
                .filter(l -> l.getLocationId().equals(locationId))
                .filter(l -> (lotId == null && l.getLotId() == null) || (lotId != null && lotId.equals(l.getLotId())))
                .map(InventoryLevel::getAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        available = available.add(deltaFlushRepository.sumPendingOnHand(tenantId, variantId, locationId, lotId));
        if (available.add(delta).compareTo(BigDecimal.ZERO) < 0) {
            throw new com.invsys.core.common.exception.InsufficientStockException("Insufficient available inventory");
        }
    }

    private void validateOnHand(BigDecimal delta, UUID variantId, UUID locationId, UUID lotId) {
        if (delta.signum() >= 0) {
            return;
        }
        boolean allowNegative = settingsRepository.findByTenantId(TenantContext.requireTenantId())
                .map(TenantSettings::getSettings)
                .map(s -> Boolean.TRUE.equals(s.get("allow_negative_inventory")))
                .orElse(false);
        if (allowNegative) {
            return;
        }
        UUID tenantId = TenantContext.requireTenantId();
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(tenantId, variantId);
        BigDecimal onHand = levels.stream()
                .filter(l -> l.getLocationId().equals(locationId))
                .filter(l -> (lotId == null && l.getLotId() == null) || (lotId != null && lotId.equals(l.getLotId())))
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Include unflushed async deltas so outbound checks stay correct under load.
        onHand = onHand.add(deltaFlushRepository.sumPendingOnHand(tenantId, variantId, locationId, lotId));
        if (onHand.add(delta).compareTo(BigDecimal.ZERO) < 0) {
            throw new com.invsys.core.common.exception.InsufficientStockException("Insufficient stock");
        }
    }
}
