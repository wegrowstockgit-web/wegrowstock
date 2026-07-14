package com.invsys.api;

import com.invsys.api.dto.CostCenterResponse;
import com.invsys.service.InternalConsumptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cost-centers")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class CostCenterController {

    private final InternalConsumptionService consumptionService;

    public CostCenterController(InternalConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @GetMapping
    public List<CostCenterResponse> list() {
        return consumptionService.listCostCenters().stream()
                .map(consumptionService::toCostCenterResponse)
                .toList();
    }

    @PostMapping
    public CostCenterResponse create(@Valid @RequestBody CreateCostCenterRequest request) {
        return consumptionService.toCostCenterResponse(
                consumptionService.createCostCenter(request.code(), request.name(), request.budget()));
    }

    @PutMapping("/{id}")
    public CostCenterResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCostCenterRequest request) {
        return consumptionService.toCostCenterResponse(
                consumptionService.updateCostCenter(id, request.name(), request.budget()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        consumptionService.deleteCostCenter(id);
    }

    public record CreateCostCenterRequest(
            @NotBlank String code,
            @NotBlank String name,
            BigDecimal budget
    ) {
    }

    public record UpdateCostCenterRequest(
            String name,
            BigDecimal budget
    ) {
    }
}
