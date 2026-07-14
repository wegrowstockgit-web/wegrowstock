package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Allocation;
import com.invsys.domain.Bom;
import com.invsys.domain.BomLine;
import com.invsys.domain.BomOperation;
import com.invsys.domain.BomOutput;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.ManufacturingWorkCenter;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.ProductionOrder;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.BomLineRepository;
import com.invsys.repository.BomOperationRepository;
import com.invsys.repository.BomOutputRepository;
import com.invsys.repository.BomRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ManufacturingWorkCenterRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.ProductionOrderRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class ManufacturingService {

    private final BomRepository bomRepository;
    private final BomLineRepository bomLineRepository;
    private final BomOperationRepository bomOperationRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final InventoryLevelRepository levelRepository;
    private final AllocationRepository allocationRepository;
    private final InventoryLedgerRepository ledgerRepository;
    private final LocationRepository locationRepository;
    private final ProductVariantRepository variantRepository;
    private final ManufacturingWorkCenterRepository workCenterRepository;
    private final DocumentSequenceService sequenceService;
    private final CostingService costingService;
    private final ManufacturingLaborService laborService;
    private final BomOutputRepository bomOutputRepository;

    public ManufacturingService(BomRepository bomRepository,
                              BomLineRepository bomLineRepository,
                              BomOperationRepository bomOperationRepository,
                              ProductionOrderRepository productionOrderRepository,
                              InventoryLevelRepository levelRepository,
                              AllocationRepository allocationRepository,
                              InventoryLedgerRepository ledgerRepository,
                              LocationRepository locationRepository,
                              ProductVariantRepository variantRepository,
                              ManufacturingWorkCenterRepository workCenterRepository,
                              DocumentSequenceService sequenceService,
                              CostingService costingService,
                              ManufacturingLaborService laborService,
                              BomOutputRepository bomOutputRepository) {
        this.bomRepository = bomRepository;
        this.bomLineRepository = bomLineRepository;
        this.bomOperationRepository = bomOperationRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.levelRepository = levelRepository;
        this.allocationRepository = allocationRepository;
        this.ledgerRepository = ledgerRepository;
        this.locationRepository = locationRepository;
        this.variantRepository = variantRepository;
        this.workCenterRepository = workCenterRepository;
        this.sequenceService = sequenceService;
        this.costingService = costingService;
        this.laborService = laborService;
        this.bomOutputRepository = bomOutputRepository;
    }

    @Transactional
    public Bom createBom(UUID parentVariantId, String name, List<BomLineInput> lines) {
        return createBom(parentVariantId, name, lines, false);
    }

    @Transactional
    public Bom createBom(UUID parentVariantId, String name, List<BomLineInput> lines, boolean autoAssemble) {
        UUID tenantId = TenantContext.requireTenantId();
        if (bomRepository.findByTenantIdAndParentVariantId(tenantId, parentVariantId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "BOM_EXISTS", "BOM already exists for variant");
        }
        for (BomLineInput line : lines) {
            if (bomLineRepository.wouldCreateCycle(tenantId, parentVariantId, line.componentVariantId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOM_CYCLE", "BOM would create a cycle");
            }
        }
        Bom bom = new Bom();
        bom.setTenantId(tenantId);
        bom.setParentVariantId(parentVariantId);
        bom.setName(name);
        bom.setAutoAssemble(autoAssemble);
        bom = bomRepository.save(bom);
        for (BomLineInput line : lines) {
            BomLine bomLine = new BomLine();
            bomLine.setTenantId(tenantId);
            bomLine.setBomId(bom.getId());
            bomLine.setComponentVariantId(line.componentVariantId());
            bomLine.setQuantityRequired(line.quantityRequired());
            bomLineRepository.save(bomLine);
        }
        return bom;
    }

    @Transactional
    public BomLine addBomLine(UUID bomId, UUID componentVariantId, BigDecimal quantityRequired) {
        UUID tenantId = TenantContext.requireTenantId();
        Bom bom = bomRepository.findById(bomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "BOM not found"));
        if (bomLineRepository.wouldCreateCycle(tenantId, bom.getParentVariantId(), componentVariantId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BOM_CYCLE", "BOM would create a cycle");
        }
        BomLine bomLine = new BomLine();
        bomLine.setTenantId(tenantId);
        bomLine.setBomId(bomId);
        bomLine.setComponentVariantId(componentVariantId);
        bomLine.setQuantityRequired(quantityRequired);
        return bomLineRepository.save(bomLine);
    }

    @Transactional(readOnly = true)
    public List<BomOutput> listBomOutputs(UUID bomId) {
        UUID tenantId = TenantContext.requireTenantId();
        bomRepository.findById(bomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "BOM not found"));
        return bomOutputRepository.findByTenantIdAndBomIdOrderByOutputTypeAsc(tenantId, bomId);
    }

    @Transactional
    public BomOutput addBomOutput(UUID bomId,
                                  UUID variantId,
                                  String outputType,
                                  BigDecimal allocationRatio,
                                  BigDecimal qtyPerBatch) {
        UUID tenantId = TenantContext.requireTenantId();
        bomRepository.findById(bomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "BOM not found"));
        String type = outputType == null ? "" : outputType.trim().toUpperCase();
        if (!List.of("MAIN", "CO_PRODUCT", "BY_PRODUCT").contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OUTPUT_TYPE",
                    "outputType must be MAIN, CO_PRODUCT, or BY_PRODUCT");
        }
        BomOutput output = new BomOutput();
        output.setTenantId(tenantId);
        output.setBomId(bomId);
        output.setVariantId(variantId);
        output.setOutputType(type);
        output.setAllocationRatio(allocationRatio != null ? allocationRatio : BigDecimal.ZERO);
        output.setQtyPerBatch(qtyPerBatch != null && qtyPerBatch.signum() > 0 ? qtyPerBatch : BigDecimal.ONE);
        return bomOutputRepository.save(output);
    }

    @Transactional
    public ProductionOrder createProductionOrder(UUID parentVariantId, BigDecimal qtyTarget) {
        UUID tenantId = TenantContext.requireTenantId();
        bomRepository.findByTenantIdAndParentVariantId(tenantId, parentVariantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_BOM", "No active BOM for variant"));
        ProductionOrder order = new ProductionOrder();
        order.setTenantId(tenantId);
        order.setNumber(sequenceService.nextNumber("PRODUCTION", "MO-{YYYY}-{seq:5}"));
        order.setParentVariantId(parentVariantId);
        order.setQtyTarget(qtyTarget);
        order.setStatus("DRAFT");
        return productionOrderRepository.save(order);
    }

    @Transactional
    public ProductionOrder allocateComponents(UUID productionOrderId) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductionOrder order = getOrder(productionOrderId);
        if (!List.of("DRAFT", "COMPONENTS_ALLOCATED").contains(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Order cannot be allocated");
        }
        BigDecimal remainingQty = order.getQtyTarget().subtract(order.getQtyProduced());
        if (remainingQty.signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Nothing left to produce");
        }

        List<UUID> locationIds = locationRepository.findByTenantIdOrderByPathAsc(tenantId)
                .stream().map(l -> l.getId()).toList();

        List<Object[]> exploded = bomLineRepository.explodeBom(tenantId, order.getParentVariantId());
        if (exploded.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_BOM", "BOM has no components");
        }

        for (Object[] row : exploded) {
            UUID componentVariantId = toUuid(row[0]);
            BigDecimal qtyPerUnit = toDecimal(row[1]);
            BigDecimal needed = qtyPerUnit.multiply(remainingQty);
            allocateForComponent(tenantId, productionOrderId, componentVariantId, needed, locationIds);
        }

        order.setStatus("COMPONENTS_ALLOCATED");
        return productionOrderRepository.save(order);
    }

    private void allocateForComponent(UUID tenantId, UUID productionOrderId, UUID variantId,
                                      BigDecimal needed, List<UUID> locationIds) {
        BigDecimal remaining = needed;
        List<InventoryLevel> levels = levelRepository.findAvailableForAllocation(tenantId, variantId, locationIds);
        for (InventoryLevel level : levels) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal available = level.getAvailable();
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal qty = available.min(remaining);
            Allocation allocation = new Allocation();
            allocation.setTenantId(tenantId);
            allocation.setProductionOrderId(productionOrderId);
            allocation.setVariantId(variantId);
            allocation.setLocationId(level.getLocationId());
            allocation.setLotId(level.getLotId());
            allocation.setQuantity(qty);
            allocation.setStatus("ACTIVE");
            allocationRepository.save(allocation);
            remaining = remaining.subtract(qty);
        }
        if (remaining.signum() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                    "Insufficient components for variant " + variantId);
        }
    }

    @Transactional
    public ProductionOrder executeAssembly(UUID productionOrderId, BigDecimal qtyToProduce) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductionOrder order = getOrder(productionOrderId);
        if (!List.of("COMPONENTS_ALLOCATED", "WIP", "IN_ROUTING").contains(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Order is not ready for assembly");
        }
        BigDecimal qtyRemaining = order.getQtyTarget().subtract(order.getQtyProduced());
        if (qtyToProduce.compareTo(qtyRemaining) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QTY", "Quantity exceeds remaining target");
        }

        List<Object[]> exploded = bomLineRepository.explodeBom(tenantId, order.getParentVariantId());
        List<Allocation> allocations = allocationRepository.findByProductionOrderIdAndStatus(productionOrderId, "ACTIVE");

        UUID outputLocation = locationRepository.findByTenantIdOrderByPathAsc(tenantId).stream()
                .findFirst()
                .map(l -> l.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_LOCATION", "No warehouse location"));

        BigDecimal materialCost = BigDecimal.ZERO;

        for (Object[] row : exploded) {
            UUID componentVariantId = toUuid(row[0]);
            BigDecimal qtyPerUnit = toDecimal(row[1]);
            BigDecimal needed = qtyPerUnit.multiply(qtyToProduce);
            BigDecimal remainingNeeded = needed;

            for (Allocation allocation : allocations) {
                if (!allocation.getVariantId().equals(componentVariantId) || remainingNeeded.signum() <= 0) {
                    continue;
                }
                if (!"ACTIVE".equals(allocation.getStatus())) {
                    continue;
                }
                BigDecimal consumeQty = allocation.getQuantity().min(remainingNeeded);
                BigDecimal unitCost = costingService.snapshotShipCost(allocation.getVariantId());
                materialCost = materialCost.add(unitCost.multiply(consumeQty));

                consumeAllocation(allocation, consumeQty);

                InventoryLedger out = new InventoryLedger();
                out.setTenantId(tenantId);
                out.setVariantId(allocation.getVariantId());
                out.setLocationId(allocation.getLocationId());
                out.setLotId(allocation.getLotId());
                out.setMovementType("ASSEMBLY_OUT");
                out.setQuantityDelta(consumeQty.negate());
                out.setUnitCost(unitCost);
                out.setReferenceType("PRODUCTION_ORDER");
                out.setReferenceId(productionOrderId);
                out.setCreatedBy(TenantContext.getUserId().orElse(null));
                ledgerRepository.save(out);
                remainingNeeded = remainingNeeded.subtract(consumeQty);
            }
            if (remainingNeeded.signum() > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_ALLOCATION",
                        "Insufficient allocated components for variant " + componentVariantId);
            }
        }

        BigDecimal laborCost = laborService.totalLaborCost(productionOrderId);
        BigDecimal totalCost = materialCost.add(laborCost);

        Bom bom = bomRepository.findByTenantIdAndParentVariantId(tenantId, order.getParentVariantId()).orElse(null);
        List<BomOutput> outputs = bom == null
                ? List.of()
                : bomOutputRepository.findByTenantIdAndBomIdOrderByOutputTypeAsc(tenantId, bom.getId());

        if (outputs.isEmpty()) {
            BigDecimal finishedUnitCost = totalCost.divide(qtyToProduce, 4, RoundingMode.HALF_UP);
            InventoryLedger in = new InventoryLedger();
            in.setTenantId(tenantId);
            in.setVariantId(order.getParentVariantId());
            in.setLocationId(outputLocation);
            in.setMovementType("ASSEMBLY_IN");
            in.setQuantityDelta(qtyToProduce);
            in.setUnitCost(finishedUnitCost);
            in.setReferenceType("PRODUCTION_ORDER");
            in.setReferenceId(productionOrderId);
            in.setCreatedBy(TenantContext.getUserId().orElse(null));
            ledgerRepository.save(in);
            costingService.applyReceiveCost(order.getParentVariantId(), qtyToProduce, finishedUnitCost);
        } else {
            BigDecimal ratioSum = outputs.stream()
                    .map(BomOutput::getAllocationRatio)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (ratioSum.signum() <= 0) {
                ratioSum = BigDecimal.valueOf(100);
            }
            for (BomOutput output : outputs) {
                BigDecimal share = output.getAllocationRatio().divide(ratioSum, 8, RoundingMode.HALF_UP);
                BigDecimal outputQty = output.getQtyPerBatch().multiply(qtyToProduce);
                if (outputQty.signum() <= 0) {
                    continue;
                }
                BigDecimal outputCost = totalCost.multiply(share);
                BigDecimal unitCost = outputCost.divide(outputQty, 4, RoundingMode.HALF_UP);
                InventoryLedger in = new InventoryLedger();
                in.setTenantId(tenantId);
                in.setVariantId(output.getVariantId());
                in.setLocationId(outputLocation);
                in.setMovementType("ASSEMBLY_IN");
                in.setQuantityDelta(outputQty);
                in.setUnitCost(unitCost);
                in.setReferenceType("PRODUCTION_ORDER");
                in.setReferenceId(productionOrderId);
                in.setReasonCode(output.getOutputType());
                in.setCreatedBy(TenantContext.getUserId().orElse(null));
                ledgerRepository.save(in);
                costingService.applyReceiveCost(output.getVariantId(), outputQty, unitCost);
            }
        }

        order.setQtyProduced(order.getQtyProduced().add(qtyToProduce));
        if (order.getQtyProduced().compareTo(order.getQtyTarget()) >= 0) {
            order.setStatus("COMPLETED");
        } else {
            order.setStatus("WIP");
            if (order.getCurrentWorkCenterId() == null) {
                assignFirstWorkCenter(order);
            }
        }
        return productionOrderRepository.save(order);
    }

    @Transactional
    public ProductionOrder advanceWorkCenter(UUID orderId) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductionOrder order = getOrder(orderId);
        if (!List.of("WIP", "IN_ROUTING", "COMPONENTS_ALLOCATED").contains(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Order cannot advance work center in current status");
        }
        Bom bom = bomRepository.findByTenantIdAndParentVariantId(tenantId, order.getParentVariantId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_BOM", "No BOM for variant"));
        List<BomOperation> operations = bomOperationRepository
                .findByTenantIdAndBomIdOrderBySequenceOrderAsc(tenantId, bom.getId()).stream()
                .filter(op -> op.getWorkCenterId() != null)
                .toList();
        if (operations.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_ROUTING", "BOM has no routed work centers");
        }

        if (order.getCurrentWorkCenterId() == null) {
            order.setCurrentWorkCenterId(operations.getFirst().getWorkCenterId());
            order.setStatus("IN_ROUTING");
            return productionOrderRepository.save(order);
        }

        int currentIndex = -1;
        for (int i = 0; i < operations.size(); i++) {
            if (order.getCurrentWorkCenterId().equals(operations.get(i).getWorkCenterId())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0 || currentIndex >= operations.size() - 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ROUTING_COMPLETE",
                    "No further work center in routing sequence");
        }
        order.setCurrentWorkCenterId(operations.get(currentIndex + 1).getWorkCenterId());
        order.setStatus("IN_ROUTING");
        return productionOrderRepository.save(order);
    }

    private void assignFirstWorkCenter(ProductionOrder order) {
        UUID tenantId = order.getTenantId();
        Bom bom = bomRepository.findByTenantIdAndParentVariantId(tenantId, order.getParentVariantId()).orElse(null);
        if (bom == null) {
            return;
        }
        List<BomOperation> operations = bomOperationRepository
                .findByTenantIdAndBomIdOrderBySequenceOrderAsc(tenantId, bom.getId()).stream()
                .filter(op -> op.getWorkCenterId() != null)
                .toList();
        for (BomOperation operation : operations) {
            ManufacturingWorkCenter wc = workCenterRepository.findById(operation.getWorkCenterId()).orElse(null);
            if (wc != null && "ACTIVE".equals(wc.getOperationalStatus())) {
                order.setCurrentWorkCenterId(wc.getId());
                order.setStatus("IN_ROUTING");
                return;
            }
        }
        if (!operations.isEmpty()) {
            order.setCurrentWorkCenterId(operations.getFirst().getWorkCenterId());
            order.setStatus("IN_ROUTING");
        }
    }

    @Transactional
    public void disassemble(UUID variantId, UUID locationId, BigDecimal qty) {
        UUID tenantId = TenantContext.requireTenantId();
        if (qty.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QTY", "Quantity must be positive");
        }

        bomRepository.findByTenantIdAndParentVariantId(tenantId, variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_BOM", "No BOM for variant"));

        BigDecimal available = levelRepository.findByTenantIdAndVariantId(tenantId, variantId).stream()
                .filter(l -> l.getLocationId().equals(locationId))
                .map(InventoryLevel::getAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(qty) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Insufficient finished goods at location");
        }

        BigDecimal parentUnitCost = costingService.snapshotShipCost(variantId);

        InventoryLedger parentOut = new InventoryLedger();
        parentOut.setTenantId(tenantId);
        parentOut.setVariantId(variantId);
        parentOut.setLocationId(locationId);
        parentOut.setMovementType("ASSEMBLY_OUT");
        parentOut.setQuantityDelta(qty.negate());
        parentOut.setUnitCost(parentUnitCost);
        parentOut.setReferenceType("DISASSEMBLY");
        parentOut.setCreatedBy(TenantContext.getUserId().orElse(null));
        ledgerRepository.save(parentOut);

        List<Object[]> exploded = bomLineRepository.explodeBom(tenantId, variantId);
        for (Object[] row : exploded) {
            UUID componentVariantId = toUuid(row[0]);
            BigDecimal qtyPerUnit = toDecimal(row[1]);
            BigDecimal componentQty = qtyPerUnit.multiply(qty);

            InventoryLedger componentIn = new InventoryLedger();
            componentIn.setTenantId(tenantId);
            componentIn.setVariantId(componentVariantId);
            componentIn.setLocationId(locationId);
            componentIn.setMovementType("ASSEMBLY_IN");
            componentIn.setQuantityDelta(componentQty);
            componentIn.setReferenceType("DISASSEMBLY");
            componentIn.setCreatedBy(TenantContext.getUserId().orElse(null));
            ledgerRepository.save(componentIn);

            ProductVariant component = variantRepository.findById(componentVariantId).orElseThrow();
            BigDecimal componentCost = component.getAvgCost() != null ? component.getAvgCost() : BigDecimal.ZERO;
            costingService.applyReceiveCost(componentVariantId, componentQty, componentCost);
        }
    }

    private void consumeAllocation(Allocation allocation, BigDecimal consumeQty) {
        if (consumeQty.compareTo(allocation.getQuantity()) < 0) {
            allocation.setQuantity(allocation.getQuantity().subtract(consumeQty));
            allocationRepository.save(allocation);
        } else {
            allocation.setStatus("CONSUMED");
            allocationRepository.save(allocation);
        }
    }

    private ProductionOrder getOrder(UUID productionOrderId) {
        return productionOrderRepository.findById(productionOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Production order not found"));
    }

    private static UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    public record BomLineInput(UUID componentVariantId, BigDecimal quantityRequired) {
    }
}
