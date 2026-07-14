package com.invsys.service;

import com.invsys.api.dto.CostCenterResponse;
import com.invsys.api.dto.InternalRequisitionLineResponse;
import com.invsys.api.dto.InternalRequisitionResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.CostCenter;
import com.invsys.domain.InternalRequisition;
import com.invsys.domain.InternalRequisitionLine;
import com.invsys.domain.InventoryLevel;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.CostCenterRepository;
import com.invsys.repository.InternalRequisitionLineRepository;
import com.invsys.repository.InternalRequisitionRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InternalConsumptionService {

    private final CostCenterRepository costCenterRepository;
    private final InternalRequisitionRepository requisitionRepository;
    private final InternalRequisitionLineRepository lineRepository;
    private final InventoryLevelRepository levelRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;

    public InternalConsumptionService(CostCenterRepository costCenterRepository,
                                      InternalRequisitionRepository requisitionRepository,
                                      InternalRequisitionLineRepository lineRepository,
                                      InventoryLevelRepository levelRepository,
                                      ProductVariantRepository variantRepository,
                                      InventoryService inventoryService) {
        this.costCenterRepository = costCenterRepository;
        this.requisitionRepository = requisitionRepository;
        this.lineRepository = lineRepository;
        this.levelRepository = levelRepository;
        this.variantRepository = variantRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public CostCenter createCostCenter(String code, String name, BigDecimal budget) {
        UUID tenantId = TenantContext.requireTenantId();
        if (costCenterRepository.findByTenantIdAndCode(tenantId, code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "Cost center code already exists");
        }
        CostCenter cc = new CostCenter();
        cc.setTenantId(tenantId);
        cc.setCode(code.trim());
        cc.setName(name.trim());
        cc.setBudget(budget);
        return costCenterRepository.save(cc);
    }

    @Transactional(readOnly = true)
    public List<CostCenter> listCostCenters() {
        return costCenterRepository.findByTenantIdOrderByCodeAsc(TenantContext.requireTenantId());
    }

    @Transactional
    public CostCenter updateCostCenter(UUID id, String name, BigDecimal budget) {
        CostCenter cc = requireCostCenter(id);
        if (name != null && !name.isBlank()) {
            cc.setName(name.trim());
        }
        cc.setBudget(budget);
        return costCenterRepository.save(cc);
    }

    @Transactional
    public void deleteCostCenter(UUID id) {
        CostCenter cc = requireCostCenter(id);
        costCenterRepository.delete(cc);
    }

    @Transactional
    public InternalRequisitionResponse createRequisition(UUID costCenterId, List<RequisitionLineInput> lines) {
        UUID tenantId = TenantContext.requireTenantId();
        CostCenter cc = requireCostCenter(costCenterId);
        if (lines == null || lines.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_LINES", "At least one line is required");
        }

        InternalRequisition req = new InternalRequisition();
        req.setTenantId(tenantId);
        req.setCostCenterId(cc.getId());
        req.setRequestedByUserId(TenantContext.getUserId().orElse(null));
        req.setStatus("DRAFT");
        req.setRequisitionNumber("REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        req = requisitionRepository.save(req);

        for (RequisitionLineInput input : lines) {
            if (input.qtyRequested() == null || input.qtyRequested().signum() <= 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_QTY", "qtyRequested must be positive");
            }
            variantRepository.findById(input.variantId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
            InternalRequisitionLine line = new InternalRequisitionLine();
            line.setTenantId(tenantId);
            line.setRequisitionId(req.getId());
            line.setVariantId(input.variantId());
            line.setQtyRequested(input.qtyRequested());
            line.setQtyIssued(BigDecimal.ZERO);
            lineRepository.save(line);
        }
        return toResponse(req);
    }

    @Transactional(readOnly = true)
    public List<InternalRequisitionResponse> listRequisitions(String status) {
        UUID tenantId = TenantContext.requireTenantId();
        List<InternalRequisition> list = status != null && !status.isBlank()
                ? requisitionRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : requisitionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InternalRequisitionResponse getRequisition(UUID id) {
        return toResponse(requireRequisition(id));
    }

    @Transactional
    public InternalRequisitionResponse approveRequisition(UUID id) {
        InternalRequisition req = requireRequisition(id);
        if (!"DRAFT".equals(req.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Only DRAFT requisitions can be approved");
        }
        req.setStatus("APPROVED");
        return toResponse(requisitionRepository.save(req));
    }

    @Transactional
    public InternalRequisitionResponse cancelRequisition(UUID id) {
        InternalRequisition req = requireRequisition(id);
        if ("ISSUED".equals(req.getStatus()) || "CANCELLED".equals(req.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Cannot cancel " + req.getStatus() + " requisition");
        }
        req.setStatus("CANCELLED");
        return toResponse(requisitionRepository.save(req));
    }

    @Transactional
    public InternalRequisitionResponse issueRequisition(UUID requisitionId, UUID locationId) {
        UUID tenantId = TenantContext.requireTenantId();
        InternalRequisition req = requireRequisition(requisitionId);
        if (!"APPROVED".equals(req.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS", "Requisition must be APPROVED to issue");
        }

        List<InternalRequisitionLine> lines = lineRepository
                .findByTenantIdAndRequisitionIdOrderByCreatedAtAsc(tenantId, req.getId());

        for (InternalRequisitionLine line : lines) {
            BigDecimal remaining = line.getQtyRequested().subtract(line.getQtyIssued());
            if (remaining.signum() <= 0) {
                continue;
            }

            List<InventoryLevel> levels = levelRepository.findAvailableForAllocation(
                    tenantId, line.getVariantId(), List.of(locationId));
            BigDecimal issued = BigDecimal.ZERO;
            for (InventoryLevel level : levels) {
                if (remaining.signum() <= 0) {
                    break;
                }
                levelRepository.lockLevelForAllocation(
                        tenantId, line.getVariantId(), level.getLocationId(), level.getLotId());
                BigDecimal take = level.getAvailable().min(remaining);
                if (take.signum() <= 0) {
                    continue;
                }
                inventoryService.consumeInternal(
                        line.getVariantId(), level.getLocationId(), level.getLotId(), take, line.getId());
                issued = issued.add(take);
                remaining = remaining.subtract(take);
            }

            if (remaining.signum() > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                        "Insufficient available inventory for variant " + line.getVariantId());
            }
            line.setQtyIssued(line.getQtyIssued().add(issued));
            lineRepository.save(line);
        }

        boolean fullyIssued = lines.stream()
                .allMatch(l -> l.getQtyIssued().compareTo(l.getQtyRequested()) >= 0);
        if (fullyIssued) {
            req.setStatus("ISSUED");
            requisitionRepository.save(req);
        }
        return toResponse(req);
    }

    public CostCenterResponse toCostCenterResponse(CostCenter cc) {
        return new CostCenterResponse(cc.getId(), cc.getCode(), cc.getName(), cc.getBudget(), cc.getCreatedAt());
    }

    private InternalRequisitionResponse toResponse(InternalRequisition req) {
        UUID tenantId = TenantContext.requireTenantId();
        String costCenterCode = costCenterRepository.findByTenantIdAndId(tenantId, req.getCostCenterId())
                .map(CostCenter::getCode)
                .orElse(null);
        List<InternalRequisitionLine> lines = lineRepository
                .findByTenantIdAndRequisitionIdOrderByCreatedAtAsc(tenantId, req.getId());
        Map<UUID, String> skus = variantRepository.findAllById(
                        lines.stream().map(InternalRequisitionLine::getVariantId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, ProductVariant::getSku));

        List<InternalRequisitionLineResponse> lineResponses = new ArrayList<>();
        for (InternalRequisitionLine line : lines) {
            lineResponses.add(new InternalRequisitionLineResponse(
                    line.getId(),
                    line.getVariantId(),
                    skus.get(line.getVariantId()),
                    line.getQtyRequested(),
                    line.getQtyIssued()));
        }
        return new InternalRequisitionResponse(
                req.getId(),
                req.getRequisitionNumber(),
                req.getCostCenterId(),
                costCenterCode,
                req.getRequestedByUserId(),
                req.getStatus(),
                req.getCreatedAt(),
                lineResponses);
    }

    private CostCenter requireCostCenter(UUID id) {
        return costCenterRepository.findByTenantIdAndId(TenantContext.requireTenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Cost center not found"));
    }

    private InternalRequisition requireRequisition(UUID id) {
        return requisitionRepository.findByTenantIdAndId(TenantContext.requireTenantId(), id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Requisition not found"));
    }

    public record RequisitionLineInput(UUID variantId, BigDecimal qtyRequested) {
    }
}
