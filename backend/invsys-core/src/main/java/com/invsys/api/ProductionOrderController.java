package com.invsys.api;

import com.invsys.api.dto.ProductionOrderResponse;
import com.invsys.core.common.OffsetPaging;
import com.invsys.core.common.PageResponse;
import com.invsys.domain.ProductionOrder;
import com.invsys.repository.ProductionOrderRepository;
import com.invsys.api.dto.ProductionTimesheetResponse;
import com.invsys.service.ManufacturingDtoMapper;
import com.invsys.service.ManufacturingLaborService;
import com.invsys.service.ManufacturingService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manufacturing")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class ProductionOrderController {

    private final ProductionOrderRepository productionOrderRepository;
    private final ManufacturingService manufacturingService;
    private final ManufacturingLaborService laborService;
    private final ManufacturingDtoMapper dtoMapper;

    public ProductionOrderController(ProductionOrderRepository productionOrderRepository,
                                     ManufacturingService manufacturingService,
                                     ManufacturingLaborService laborService,
                                     ManufacturingDtoMapper dtoMapper) {
        this.productionOrderRepository = productionOrderRepository;
        this.manufacturingService = manufacturingService;
        this.laborService = laborService;
        this.dtoMapper = dtoMapper;
    }

    private static final Set<String> ORDER_SORT = Set.of("createdAt", "number", "status");

    @GetMapping("/orders")
    public PageResponse<ProductionOrderResponse> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        Page<ProductionOrder> result = productionOrderRepository.search(
                TenantContext.requireTenantId(),
                OffsetPaging.keyword(search),
                OffsetPaging.of(page, size, sort, "createdAt", Sort.Direction.DESC, ORDER_SORT));
        List<ProductionOrderResponse> items = result.getContent().stream()
                .map(dtoMapper::toProductionOrderResponse)
                .toList();
        return PageResponse.of(result, items);
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

    @PostMapping("/orders/{id}/release")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PRODUCTION_SUPERVISOR')")
    public ProductionOrderResponse release(@PathVariable UUID id) {
        return dtoMapper.toProductionOrderResponse(manufacturingService.releaseToFloor(id));
    }

    @PostMapping("/orders/{id}/scrap")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PRODUCTION_SUPERVISOR')")
    public ProductionOrderResponse scrap(@PathVariable UUID id, @Valid @RequestBody ScrapRequest request) {
        manufacturingService.logScrap(id, request.variantId(), request.locationId(), request.quantity());
        return dtoMapper.toProductionOrderResponse(productionOrderRepository.findById(id).orElseThrow());
    }

    @PostMapping("/orders/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PRODUCTION_SUPERVISOR')")
    public ProductionOrderResponse complete(@PathVariable UUID id, @Valid @RequestBody AssembleRequest request) {
        return dtoMapper.toProductionOrderResponse(manufacturingService.executeAssembly(id, request.qtyToProduce()));
    }

    @PostMapping("/orders/{id}/labor")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PRODUCTION_SUPERVISOR','PICKER')")
    public ProductionTimesheetResponse logLabor(
            @PathVariable UUID id,
            @Valid @RequestBody LogLaborRequest request) {
        return laborService.toResponse(laborService.logHours(id, request.operationId(), request.hours()));
    }

    public record CreateProductionOrderRequest(
            @NotNull UUID parentVariantId,
            @NotNull @Positive BigDecimal qtyTarget
    ) {
    }

    public record AssembleRequest(@NotNull @Positive BigDecimal qtyToProduce) {
    }

    public record ScrapRequest(
            @NotNull UUID variantId,
            UUID locationId,
            @NotNull @Positive BigDecimal quantity
    ) {
    }

    public record LogLaborRequest(
            @NotNull UUID operationId,
            @NotNull @Positive BigDecimal hours
    ) {
    }
}
