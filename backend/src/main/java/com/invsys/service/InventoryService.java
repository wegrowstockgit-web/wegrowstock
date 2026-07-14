package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.integration.OutboxService;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SerialNumberRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryLedgerRepository ledgerRepository;
    private final InventoryLevelRepository levelRepository;
    private final TenantSettingsRepository settingsRepository;
    private final AllocationRepository allocationRepository;
    private final ProductVariantRepository variantRepository;
    private final CostingService costingService;
    private final OutboxService outboxService;
    private final SerialNumberService serialNumberService;
    private final SerialNumberRepository serialNumberRepository;
    private final CycleCountService cycleCountService;

    public InventoryService(InventoryLedgerRepository ledgerRepository,
                            InventoryLevelRepository levelRepository,
                            TenantSettingsRepository settingsRepository,
                            AllocationRepository allocationRepository,
                            ProductVariantRepository variantRepository,
                            CostingService costingService,
                            OutboxService outboxService,
                            SerialNumberService serialNumberService,
                            SerialNumberRepository serialNumberRepository,
                            CycleCountService cycleCountService) {
        this.ledgerRepository = ledgerRepository;
        this.levelRepository = levelRepository;
        this.settingsRepository = settingsRepository;
        this.allocationRepository = allocationRepository;
        this.variantRepository = variantRepository;
        this.costingService = costingService;
        this.outboxService = outboxService;
        this.serialNumberService = serialNumberService;
        this.serialNumberRepository = serialNumberRepository;
        this.cycleCountService = cycleCountService;
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
        costingService.applyReceiveCost(variantId, quantity.abs(), unitCost);
        InventoryLedger entry = appendMovement("RECEIVE", variantId, locationId, lotId, quantity.abs(),
                null, referenceType, referenceId, null, unitCost, serialId);
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
        InventoryLedger entry = appendMovement("RECEIVE", variantId, locationId, lotId, qty,
                "RMA_QUARANTINE", referenceType, referenceId, null, null, null);

        Allocation hold = new Allocation();
        hold.setTenantId(TenantContext.requireTenantId());
        hold.setSalesOrderLineId(salesOrderLineId);
        hold.setVariantId(variantId);
        hold.setLocationId(locationId);
        hold.setLotId(lotId);
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
        return appendMovement("ADJUST", variantId, locationId, lotId, delta, reasonCode, null, null, null, null, serialId);
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
        validateNegative(quantity.negate(), variantId, fromLocationId, lotId);
        UUID groupId = UUID.randomUUID();
        appendMovement("TRANSFER_OUT", variantId, fromLocationId, lotId, quantity.negate(), null, null, null, groupId, null, null);
        appendMovement("TRANSFER_IN", variantId, toLocationId, lotId, quantity.abs(), null, null, null, groupId, null, null);
        return groupId;
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
        validateNegative(quantity.negate(), allocation.getVariantId(), allocation.getLocationId(), allocation.getLotId());
        allocation.setStatus("CONSUMED");
        allocationRepository.save(allocation);
        BigDecimal unitCost = costingService.snapshotShipCost(allocation.getVariantId());
        InventoryLedger entry = appendMovement("SHIP", allocation.getVariantId(), allocation.getLocationId(),
                allocation.getLotId(), quantity.negate(), null, "SALES_ORDER_LINE", allocation.getSalesOrderLineId(),
                null, unitCost, serialId);
        emitIntegrationEvents(entry, allocation.getVariantId());
        return entry;
    }

    private InventoryLedger appendMovement(String type, UUID variantId, UUID locationId, UUID lotId,
                                           BigDecimal delta, String reasonCode, String refType, UUID refId,
                                           UUID transferGroupId, BigDecimal unitCost, UUID serialNumberId) {
        InventoryLedger entry = new InventoryLedger();
        entry.setTenantId(TenantContext.requireTenantId());
        entry.setVariantId(variantId);
        entry.setLocationId(locationId);
        entry.setLotId(lotId);
        entry.setMovementType(type);
        entry.setQuantityDelta(delta);
        entry.setReasonCode(reasonCode);
        entry.setReferenceType(refType);
        entry.setReferenceId(refId);
        entry.setTransferGroupId(transferGroupId);
        entry.setUnitCost(unitCost);
        entry.setSerialNumberId(serialNumberId);
        entry.setCreatedBy(TenantContext.getUserId().orElse(null));
        InventoryLedger saved = ledgerRepository.save(entry);
        if ("ADJUST".equals(type) || "TRANSFER_OUT".equals(type) || "TRANSFER_IN".equals(type) || "SHIP".equals(type)) {
            cycleCountService.evaluateLocationVelocity(locationId);
        }
        return saved;
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
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(TenantContext.requireTenantId(), variantId);
        BigDecimal available = levels.stream()
                .filter(l -> l.getLocationId().equals(locationId))
                .filter(l -> (lotId == null && l.getLotId() == null) || (lotId != null && lotId.equals(l.getLotId())))
                .map(InventoryLevel::getAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.add(delta).compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Insufficient available inventory");
        }
    }
}
