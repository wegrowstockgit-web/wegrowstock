package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.RtvOrder;
import com.invsys.domain.RtvOrderLine;
import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.fulfillment.domain.FulfillmentException;
import com.invsys.modules.purchasing.domain.ApInvoiceIngestion;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.inventory.repository.AllocationRepository;
import com.invsys.modules.fulfillment.repository.FulfillmentExceptionRepository;
import com.invsys.modules.purchasing.repository.ApInvoiceIngestionRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.repository.RtvOrderLineRepository;
import com.invsys.repository.RtvOrderRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReturnToVendorService {

    private static final Set<String> VALID_REASON_CODES = Set.of(
            "DAMAGED_ON_RECEIPT", "DEFECTIVE", "OVER_SHIPMENT");

    private final RtvOrderRepository rtvOrderRepository;
    private final RtvOrderLineRepository rtvOrderLineRepository;
    private final FulfillmentExceptionRepository exceptionRepository;
    private final AllocationRepository allocationRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ApInvoiceIngestionRepository apInvoiceIngestionRepository;
    private final InventoryService inventoryService;
    private final CostingService costingService;

    public ReturnToVendorService(RtvOrderRepository rtvOrderRepository,
                                 RtvOrderLineRepository rtvOrderLineRepository,
                                 FulfillmentExceptionRepository exceptionRepository,
                                 AllocationRepository allocationRepository,
                                 PurchaseOrderRepository purchaseOrderRepository,
                                 PurchaseOrderLineRepository purchaseOrderLineRepository,
                                 ApInvoiceIngestionRepository apInvoiceIngestionRepository,
                                 InventoryService inventoryService,
                                 CostingService costingService) {
        this.rtvOrderRepository = rtvOrderRepository;
        this.rtvOrderLineRepository = rtvOrderLineRepository;
        this.exceptionRepository = exceptionRepository;
        this.allocationRepository = allocationRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.apInvoiceIngestionRepository = apInvoiceIngestionRepository;
        this.inventoryService = inventoryService;
        this.costingService = costingService;
    }

    @Transactional(readOnly = true)
    public List<RtvOrder> listRtv() {
        return rtvOrderRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId());
    }

    @Transactional(readOnly = true)
    public RtvDetail get(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        RtvOrder order = rtvOrderRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "RTV order not found"));
        List<RtvOrderLine> lines = rtvOrderLineRepository.findByTenantIdAndRtvOrderId(tenantId, id);
        return new RtvDetail(order, lines);
    }

    @Transactional
    public RtvDetail createRtvFromException(UUID exceptionId,
                                            String reasonCode,
                                            BigDecimal qty,
                                            UUID supplierId,
                                            UUID purchaseOrderId) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalizedReason = normalizeReasonCode(reasonCode);
        if (qty == null || qty.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QTY", "Quantity must be positive");
        }

        FulfillmentException exception = exceptionRepository.findByTenantIdAndId(tenantId, exceptionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Exception not found"));
        Allocation allocation = allocationRepository.findByTenantIdAndId(tenantId, exception.getAllocationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Allocation not found"));

        UUID resolvedSupplierId = resolveSupplierId(tenantId, supplierId, purchaseOrderId);
        BigDecimal unitCost = resolveUnitCost(purchaseOrderId, allocation.getVariantId());

        RtvOrder order = new RtvOrder();
        order.setTenantId(tenantId);
        order.setSupplierId(resolvedSupplierId);
        order.setPurchaseOrderId(purchaseOrderId);
        order.setNumber("RTV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setStatus("DRAFT");
        order.setExceptionId(exceptionId);
        order = rtvOrderRepository.save(order);

        RtvOrderLine line = new RtvOrderLine();
        line.setTenantId(tenantId);
        line.setRtvOrderId(order.getId());
        line.setVariantId(allocation.getVariantId());
        line.setLotId(allocation.getLotId());
        line.setLocationId(allocation.getLocationId());
        line.setQtyReturned(qty);
        line.setUnitCost(unitCost);
        line.setReasonCode(normalizedReason);
        line = rtvOrderLineRepository.save(line);

        order.setTotalChargebackAmount(qty.multiply(unitCost).setScale(4, RoundingMode.HALF_UP));
        order = rtvOrderRepository.save(order);

        return new RtvDetail(order, List.of(line));
    }

    @Transactional
    public RtvOrder approve(UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        RtvOrder order = rtvOrderRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "RTV order not found"));
        if (!"DRAFT".equalsIgnoreCase(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Only DRAFT RTV orders can be approved");
        }
        order.setStatus("APPROVED");
        return rtvOrderRepository.save(order);
    }

    @Transactional
    public RtvDetail shipRtv(UUID rtvOrderId, String carrier, String trackingNumber) {
        UUID tenantId = TenantContext.requireTenantId();
        RtvOrder order = rtvOrderRepository.findByTenantIdAndId(tenantId, rtvOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "RTV order not found"));
        if (!"APPROVED".equalsIgnoreCase(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "RTV must be APPROVED before shipping");
        }

        List<RtvOrderLine> lines = rtvOrderLineRepository.findByTenantIdAndRtvOrderId(tenantId, rtvOrderId);
        if (lines.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_LINES", "RTV has no lines");
        }

        BigDecimal totalChargeback = BigDecimal.ZERO;
        for (RtvOrderLine line : lines) {
            inventoryService.shipVendorReturn(
                    line.getVariantId(),
                    line.getLocationId(),
                    line.getLotId(),
                    line.getQtyReturned(),
                    rtvOrderId);
            totalChargeback = totalChargeback.add(
                    line.getQtyReturned().multiply(line.getUnitCost()));
        }

        order.setStatus("SHIPPED");
        order.setCarrier(carrier);
        order.setTrackingNumber(trackingNumber);
        order.setDebitMemoNumber("DM-" + order.getNumber());
        order.setTotalChargebackAmount(totalChargeback.setScale(4, RoundingMode.HALF_UP));
        order = rtvOrderRepository.save(order);

        createChargebackIngestion(tenantId, order);

        return new RtvDetail(order, lines);
    }

    private void createChargebackIngestion(UUID tenantId, RtvOrder order) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", "RTV_DEBIT_MEMO");
        metadata.put("debitMemoNumber", order.getDebitMemoNumber());
        metadata.put("rtvOrderId", order.getId().toString());
        metadata.put("rtvNumber", order.getNumber());
        metadata.put("chargebackAmount", order.getTotalChargebackAmount());
        metadata.put("supplierId", order.getSupplierId().toString());

        ApInvoiceIngestion ingestion = new ApInvoiceIngestion();
        ingestion.setTenantId(tenantId);
        ingestion.setFileStorageKey("rtv-chargeback/" + order.getId());
        ingestion.setIngestionStatus("STAGED");
        ingestion.setMatchedPurchaseOrderId(order.getPurchaseOrderId());
        ingestion.setParsedMetadata(metadata);
        apInvoiceIngestionRepository.save(ingestion);
    }

    private UUID resolveSupplierId(UUID tenantId, UUID supplierId, UUID purchaseOrderId) {
        if (supplierId != null) {
            return supplierId;
        }
        if (purchaseOrderId != null) {
            PurchaseOrder po = purchaseOrderRepository.findByTenantIdAndId(tenantId, purchaseOrderId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Purchase order not found"));
            return po.getSupplierId();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_REQUIRED",
                "supplierId is required when purchaseOrderId is not provided");
    }

    private BigDecimal resolveUnitCost(UUID purchaseOrderId, UUID variantId) {
        if (purchaseOrderId != null) {
            for (PurchaseOrderLine poLine : purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrderId)) {
                if (variantId.equals(poLine.getVariantId())) {
                    return poLine.getUnitCost() != null ? poLine.getUnitCost() : BigDecimal.ZERO;
                }
            }
        }
        return costingService.snapshotShipCost(variantId);
    }

    private static String normalizeReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REASON", "reasonCode is required");
        }
        String normalized = reasonCode.trim().toUpperCase();
        if (!VALID_REASON_CODES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REASON",
                    "reasonCode must be one of: " + String.join(", ", VALID_REASON_CODES));
        }
        return normalized;
    }

    public record RtvDetail(RtvOrder order, List<RtvOrderLine> lines) {
    }
}
