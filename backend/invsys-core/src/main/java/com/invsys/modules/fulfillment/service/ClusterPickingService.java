package com.invsys.modules.fulfillment.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.PickingBatch;
import com.invsys.domain.PickingTask;
import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.fulfillment.domain.ClusterToteMapping;
import com.invsys.modules.fulfillment.domain.PickingWave;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.fulfillment.repository.ClusterToteMappingRepository;
import com.invsys.modules.fulfillment.repository.PickingWaveRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.PickingBatchRepository;
import com.invsys.repository.PickingTaskRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClusterPickingService {

    private final PickingWaveRepository waveRepository;
    private final PickingBatchRepository batchRepository;
    private final PickingTaskRepository taskRepository;
    private final AllocationRepository allocationRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final ClusterToteMappingRepository clusterToteMappingRepository;
    private final ProductVariantRepository variantRepository;

    public ClusterPickingService(PickingWaveRepository waveRepository,
                                 PickingBatchRepository batchRepository,
                                 PickingTaskRepository taskRepository,
                                 AllocationRepository allocationRepository,
                                 SalesOrderLineRepository salesOrderLineRepository,
                                 ClusterToteMappingRepository clusterToteMappingRepository,
                                 ProductVariantRepository variantRepository) {
        this.waveRepository = waveRepository;
        this.batchRepository = batchRepository;
        this.taskRepository = taskRepository;
        this.allocationRepository = allocationRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.clusterToteMappingRepository = clusterToteMappingRepository;
        this.variantRepository = variantRepository;
    }

    @Transactional
    public List<ClusterToteMapping> bindClusterCart(UUID waveId, Map<Integer, String> slotToToteBarcodes) {
        UUID tenantId = TenantContext.requireTenantId();
        PickingWave wave = waveRepository.findById(waveId)
                .filter(w -> tenantId.equals(w.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Wave not found"));

        List<PickingBatch> batches = batchRepository.findByWaveId(wave.getId());
        if (batches.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "No picking batch for wave");
        }
        PickingBatch batch = batches.getFirst();

        Map<Integer, String> slots = normalizeSlots(slotToToteBarcodes);
        validateDistinctToteBarcodes(slots);

        List<PickingTask> tasks = taskRepository.findByBatchIdOrderBySequenceOrderAsc(batch.getId());
        List<UUID> salesOrderIds = distinctSalesOrderIds(tasks);
        if (slots.size() != salesOrderIds.size()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SLOT_COUNT_MISMATCH",
                    "Expected " + salesOrderIds.size() + " tote slots for distinct sales orders, got " + slots.size());
        }

        clusterToteMappingRepository.deleteByTenantIdAndBatchId(tenantId, batch.getId());

        List<Integer> orderedSlots = slots.keySet().stream().sorted().toList();
        List<ClusterToteMapping> saved = new ArrayList<>();
        Map<UUID, Integer> salesOrderToSlot = new LinkedHashMap<>();
        for (int i = 0; i < salesOrderIds.size(); i++) {
            int slotIndex = orderedSlots.get(i);
            UUID salesOrderId = salesOrderIds.get(i);
            salesOrderToSlot.put(salesOrderId, slotIndex);

            ClusterToteMapping mapping = new ClusterToteMapping();
            mapping.setTenantId(tenantId);
            mapping.setBatchId(batch.getId());
            mapping.setSlotIndex(slotIndex);
            mapping.setToteBarcode(slots.get(slotIndex));
            mapping.setSalesOrderId(salesOrderId);
            saved.add(clusterToteMappingRepository.save(mapping));
        }

        for (PickingTask task : tasks) {
            UUID salesOrderId = resolveSalesOrderId(task);
            if (salesOrderId == null) {
                continue;
            }
            Integer slotIndex = salesOrderToSlot.get(salesOrderId);
            if (slotIndex != null) {
                task.setToteIdentifier("SLOT-" + slotIndex);
                taskRepository.save(task);
            }
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ClusterPickStep> getDirectedClusterPickSequence(UUID batchId) {
        UUID tenantId = TenantContext.requireTenantId();
        PickingBatch batch = batchRepository.findById(batchId)
                .filter(b -> tenantId.equals(b.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Batch not found"));

        Map<UUID, ClusterToteMapping> mappingBySalesOrder = clusterToteMappingRepository
                .findByTenantIdAndBatchId(tenantId, batch.getId()).stream()
                .collect(Collectors.toMap(ClusterToteMapping::getSalesOrderId, m -> m, (a, b) -> a));

        Map<UUID, String> skuByVariant = variantRepository.findAll().stream()
                .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getSku, (a, b) -> a));

        List<PickingTask> tasks = taskRepository.findByBatchIdOrderBySequenceOrderAsc(batch.getId());
        tasks.sort(Comparator
                .comparing(PickingTask::getLocationPath, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(PickingTask::getSequenceOrder));

        List<ClusterPickStep> steps = new ArrayList<>();
        for (PickingTask task : tasks) {
            Allocation allocation = allocationRepository.findById(task.getAllocationId()).orElse(null);
            if (allocation == null) {
                continue;
            }
            UUID salesOrderId = resolveSalesOrderId(task);
            ClusterToteMapping mapping = salesOrderId != null ? mappingBySalesOrder.get(salesOrderId) : null;
            String sku = skuByVariant.getOrDefault(allocation.getVariantId(), allocation.getVariantId().toString());
            BigDecimal qty = allocation.getQuantity();
            int slotIndex = mapping != null ? mapping.getSlotIndex() : 0;
            String toteBarcode = mapping != null ? mapping.getToteBarcode() : task.getToteIdentifier();
            String instruction = "Scan SKU " + sku + " -> Place Qty " + qty.stripTrailingZeros().toPlainString()
                    + " into Tote slot " + (slotIndex > 0 ? slotIndex : "?");
            steps.add(new ClusterPickStep(
                    task.getSequenceOrder(),
                    sku,
                    qty,
                    slotIndex,
                    toteBarcode,
                    task.getLocationPath(),
                    instruction));
        }
        return steps;
    }

    private static Map<Integer, String> normalizeSlots(Map<Integer, String> slotToToteBarcodes) {
        if (slotToToteBarcodes == null || slotToToteBarcodes.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SLOTS_REQUIRED", "At least one tote slot is required");
        }
        Map<Integer, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : slotToToteBarcodes.entrySet()) {
            int slot = entry.getKey();
            if (slot < 1 || slot > 12) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SLOT",
                        "Slot index must be between 1 and 12, got " + slot);
            }
            String barcode = entry.getValue();
            if (barcode == null || barcode.isBlank()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TOTE_BARCODE",
                        "Tote barcode required for slot " + slot);
            }
            normalized.put(slot, barcode.trim());
        }
        return normalized;
    }

    private static void validateDistinctToteBarcodes(Map<Integer, String> slots) {
        Set<String> seen = new LinkedHashSet<>();
        for (String barcode : slots.values()) {
            if (!seen.add(barcode)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DUPLICATE_TOTE_BARCODE",
                        "Each tote barcode must be distinct: " + barcode);
            }
        }
    }

    private List<UUID> distinctSalesOrderIds(List<PickingTask> tasks) {
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>();
        for (PickingTask task : tasks) {
            UUID salesOrderId = resolveSalesOrderId(task);
            if (salesOrderId != null) {
                ordered.add(salesOrderId);
            }
        }
        return new ArrayList<>(ordered);
    }

    private UUID resolveSalesOrderId(PickingTask task) {
        Allocation allocation = allocationRepository.findById(task.getAllocationId()).orElse(null);
        if (allocation == null || allocation.getSalesOrderLineId() == null) {
            return null;
        }
        return salesOrderLineRepository.findById(allocation.getSalesOrderLineId())
                .map(SalesOrderLine::getSalesOrderId)
                .orElse(null);
    }

    public record ClusterPickStep(
            int sequenceOrder,
            String sku,
            BigDecimal qty,
            int slotIndex,
            String toteBarcode,
            String locationPath,
            String instruction
    ) {
    }
}
