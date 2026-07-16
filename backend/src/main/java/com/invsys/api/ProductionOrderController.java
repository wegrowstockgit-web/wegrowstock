package com.invsys.api;

import com.invsys.api.dto.ProductionOrderResponse;
import com.invsys.domain.ProductionOrder;
import com.invsys.repository.ProductionOrderRepository;
import com.invsys.service.ManufacturingDtoMapper;
import com.invsys.service.ManufacturingService;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manufacturing")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class ProductionOrderController {

    private final ProductionOrderRepository productionOrderRepository;
    private final ManufacturingService manufacturingService;
    private final ManufacturingDtoMapper dtoMapper;

    public ProductionOrderController(ProductionOrderRepository productionOrderRepository,
                                     ManufacturingService manufacturingService,
                                     ManufacturingDtoMapper dtoMapper) {
        this.productionOrderRepository = productionOrderRepository;
        this.manufacturingService = manufacturingService;
        this.dtoMapper = dtoMapper;
    }

    @GetMapping("/orders")
    public List<ProductionOrderResponse> listOrders() {
        return productionOrderRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.requireTenantId()).stream()
                .map(dtoMapper::toProductionOrderResponse)
                .toList();
    }

    @GetMapping("/orders/{id}")
    public ProductionOrderResponse getOrder(@PathVariable UUID id) {
        return dtoMapper.toProductionOrderResponse(productionOrderRepository.findById(id).orElseThrow());
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ProductionOrderResponse createOrder(@Valid @RequestBody CreateProductionOrderRequest request) {
        return dtoMapper.toProductionOrderResponse(
                manufacturingService.createProductionOrder(request.parentVariantId(), request.qtyTarget()));
    }

    @PostMapping("/orders/{id}/allocate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ProductionOrderResponse allocate(@PathVariable UUID id) {
        return dtoMapper.toProductionOrderResponse(manufacturingService.allocateComponents(id));
    }

    @PostMapping("/orders/{id}/assemble")
    public ProductionOrderResponse assemble(@PathVariable UUID id, @Valid @RequestBody AssembleRequest request) {
        return dtoMapper.toProductionOrderResponse(manufacturingService.executeAssembly(id, request.qtyToProduce()));
    }

    public record CreateProductionOrderRequest(
            @NotNull UUID parentVariantId,
            @NotNull @Positive BigDecimal qtyTarget
    ) {
    }

    public record AssembleRequest(@NotNull @Positive BigDecimal qtyToProduce) {
    }
}
