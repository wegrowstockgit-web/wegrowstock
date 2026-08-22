package com.invsys.service;

import com.invsys.api.dto.ProductionTimesheetResponse;
import com.invsys.core.common.ApiException;
import com.invsys.domain.Bom;
import com.invsys.domain.BomOperation;
import com.invsys.domain.ManufacturingOperation;
import com.invsys.domain.ProductionOrder;
import com.invsys.domain.ProductionTimesheet;
import com.invsys.domain.TeamLaborRate;
import com.invsys.repository.BomOperationRepository;
import com.invsys.repository.BomRepository;
import com.invsys.repository.ManufacturingOperationRepository;
import com.invsys.repository.ProductionOrderRepository;
import com.invsys.repository.ProductionTimesheetRepository;
import com.invsys.repository.TeamLaborRateRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.invsys.domain.User;

@Service
public class ManufacturingLaborService {

    private final ProductionOrderRepository productionOrderRepository;
    private final BomRepository bomRepository;
    private final BomOperationRepository bomOperationRepository;
    private final ManufacturingOperationRepository operationRepository;
    private final ProductionTimesheetRepository timesheetRepository;
    private final TeamLaborRateRepository laborRateRepository;

    public ManufacturingLaborService(ProductionOrderRepository productionOrderRepository,
                                     BomRepository bomRepository,
                                     BomOperationRepository bomOperationRepository,
                                     ManufacturingOperationRepository operationRepository,
                                     ProductionTimesheetRepository timesheetRepository,
                                     TeamLaborRateRepository laborRateRepository) {
        this.productionOrderRepository = productionOrderRepository;
        this.bomRepository = bomRepository;
        this.bomOperationRepository = bomOperationRepository;
        this.operationRepository = operationRepository;
        this.timesheetRepository = timesheetRepository;
        this.laborRateRepository = laborRateRepository;
    }

    public List<ManufacturingOperation> listOperationsForOrder(UUID productionOrderId) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductionOrder order = productionOrderRepository.findById(productionOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Production order not found"));
        Bom bom = bomRepository.findByTenantIdAndParentVariantId(tenantId, order.getParentVariantId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_BOM", "No BOM for variant"));
        List<BomOperation> bomOps = bomOperationRepository.findByTenantIdAndBomId(tenantId, bom.getId());
        if (bomOps.isEmpty()) {
            return operationRepository.findByTenantIdOrderByNameAsc(tenantId);
        }
        return bomOps.stream()
                .map(BomOperation::getOperationId)
                .map(operationRepository::findById)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    @Transactional
    public ProductionTimesheet startTimesheet(UUID productionOrderId, UUID operationId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User required"));

        ProductionOrder order = productionOrderRepository.findById(productionOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Production order not found"));
        if (!List.of("COMPONENTS_ALLOCATED", "WIP").contains(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "Order is not active on the floor");
        }

        operationRepository.findById(operationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Operation not found"));

        timesheetRepository.findByTenantIdAndProductionOrderIdAndUserIdAndEndTimeIsNull(
                        tenantId, productionOrderId, userId)
                .ifPresent(t -> {
                    throw new ApiException(HttpStatus.CONFLICT, "TIMESHEET_OPEN",
                            "Stop the current timesheet before starting another");
                });

        ProductionTimesheet timesheet = new ProductionTimesheet();
        timesheet.setTenantId(tenantId);
        timesheet.setProductionOrderId(productionOrderId);
        timesheet.setOperationId(operationId);
        timesheet.setUserId(userId);
        timesheet.setStartTime(Instant.now());
        return timesheetRepository.save(timesheet);
    }

    @Transactional
    public ProductionTimesheet logHours(UUID productionOrderId, UUID operationId, BigDecimal hours) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User required"));
        ProductionOrder order = productionOrderRepository.findById(productionOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Production order not found"));
        if (List.of("DRAFT", "CANCELLED", "COMPLETED").contains(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Labor can only be logged while the order is on the floor");
        }
        if (hours == null || hours.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "hours must be greater than zero");
        }
        ManufacturingOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Operation not found"));
        BigDecimal hourlyRate = laborRateRepository.findByTenantIdAndUserId(tenantId, userId)
                .map(TeamLaborRate::getHourlyRate)
                .orElse(operation.getDefaultHourlyRate() != null ? operation.getDefaultHourlyRate() : BigDecimal.ZERO);
        Instant endTime = Instant.now();
        Instant startTime = endTime.minusSeconds(hours.multiply(BigDecimal.valueOf(3600)).longValue());
        ProductionTimesheet timesheet = new ProductionTimesheet();
        timesheet.setTenantId(tenantId);
        timesheet.setProductionOrderId(productionOrderId);
        timesheet.setOperationId(operationId);
        timesheet.setUserId(userId);
        timesheet.setStartTime(startTime);
        timesheet.setEndTime(endTime);
        timesheet.setTotalCost(hours.multiply(hourlyRate).setScale(4, RoundingMode.HALF_UP));
        return timesheetRepository.save(timesheet);
    }

    @Transactional
    public ProductionTimesheet stopTimesheet(UUID timesheetId) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductionTimesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Timesheet not found"));
        if (timesheet.getEndTime() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_STOPPED", "Timesheet already stopped");
        }

        ManufacturingOperation operation = operationRepository.findById(timesheet.getOperationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Operation not found"));

        BigDecimal hourlyRate = laborRateRepository.findByTenantIdAndUserId(tenantId, timesheet.getUserId())
                .map(TeamLaborRate::getHourlyRate)
                .orElse(operation.getDefaultHourlyRate());

        Instant endTime = Instant.now();
        BigDecimal hours = BigDecimal.valueOf(Duration.between(timesheet.getStartTime(), endTime).toMillis())
                .divide(BigDecimal.valueOf(3_600_000L), 4, RoundingMode.HALF_UP);

        timesheet.setEndTime(endTime);
        timesheet.setTotalCost(hours.multiply(hourlyRate).setScale(4, RoundingMode.HALF_UP));
        return timesheetRepository.save(timesheet);
    }

    public BigDecimal totalLaborCost(UUID productionOrderId) {
        return timesheetRepository.findByTenantIdAndProductionOrderId(
                        TenantContext.requireTenantId(), productionOrderId)
                .stream()
                .filter(t -> t.getEndTime() != null)
                .map(ProductionTimesheet::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<ProductionTimesheetResponse> listTimesheets(UUID productionOrderId) {
        return timesheetRepository.findByTenantIdAndProductionOrderId(
                        TenantContext.requireTenantId(), productionOrderId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ProductionTimesheetResponse toResponse(ProductionTimesheet timesheet) {
        String operationName = operationRepository.findById(timesheet.getOperationId())
                .map(ManufacturingOperation::getName)
                .orElse(null);
        return new ProductionTimesheetResponse(
                timesheet.getId(),
                timesheet.getProductionOrderId(),
                timesheet.getOperationId(),
                operationName,
                timesheet.getUserId(),
                timesheet.getStartTime(),
                timesheet.getEndTime(),
                timesheet.getTotalCost()
        );
    }
}
