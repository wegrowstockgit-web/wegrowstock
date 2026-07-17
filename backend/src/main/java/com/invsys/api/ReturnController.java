package com.invsys.api;

import com.invsys.api.dto.ReturnLineResponse;

import com.invsys.api.dto.ReturnResponse;

import com.invsys.domain.Customer;

import com.invsys.domain.Product;

import com.invsys.domain.ProductVariant;

import com.invsys.domain.ReturnLine;

import com.invsys.domain.ReturnOrder;

import com.invsys.domain.SalesOrder;

import com.invsys.domain.SalesOrderLine;

import com.invsys.repository.CustomerRepository;

import com.invsys.repository.ProductRepository;

import com.invsys.repository.ProductVariantRepository;

import com.invsys.repository.ReturnLineRepository;

import com.invsys.repository.ReturnOrderRepository;

import com.invsys.repository.SalesOrderLineRepository;

import com.invsys.repository.SalesOrderRepository;

import com.invsys.media.MediaUploadService;

import com.invsys.service.ReturnService;

import com.invsys.service.ScanService;

import com.invsys.tenancy.TenantContext;

import jakarta.validation.Valid;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;

import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.PutMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

import java.util.List;

import java.util.Map;

import java.util.UUID;

import java.util.stream.Collectors;

@RestController

@RequestMapping("/api/v1/returns")

@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")

public class ReturnController {

    private final ReturnOrderRepository returnOrderRepository;

    private final ReturnLineRepository returnLineRepository;

    private final ReturnService returnService;

    private final SalesOrderRepository salesOrderRepository;

    private final SalesOrderLineRepository salesOrderLineRepository;

    private final CustomerRepository customerRepository;

    private final ProductVariantRepository variantRepository;

    private final ProductRepository productRepository;

    private final ScanService scanService;
    private final MediaUploadService mediaUploadService;

    public ReturnController(ReturnOrderRepository returnOrderRepository,
                            ReturnLineRepository returnLineRepository,
                            ReturnService returnService,
                            SalesOrderRepository salesOrderRepository,
                            SalesOrderLineRepository salesOrderLineRepository,
                            CustomerRepository customerRepository,
                            ProductVariantRepository variantRepository,
                            ProductRepository productRepository,
                            ScanService scanService,
                            MediaUploadService mediaUploadService) {
        this.returnOrderRepository = returnOrderRepository;
        this.returnLineRepository = returnLineRepository;
        this.returnService = returnService;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.customerRepository = customerRepository;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.scanService = scanService;
        this.mediaUploadService = mediaUploadService;
    }

    @GetMapping

    public List<ReturnResponse> list(@RequestParam(required = false) String status) {

        UUID tenantId = TenantContext.requireTenantId();

        List<ReturnOrder> orders = status != null && !status.isBlank()

                ? returnOrderRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)

                : returnOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        return orders.stream().map(this::toResponse).toList();

    }

    @GetMapping("/by-barcode/{barcode}")

    public ReturnResponse byBarcode(@PathVariable String barcode) {

        return toResponse(returnService.findByBarcode(barcode));

    }

    @GetMapping("/{id}")

    public ReturnResponse get(@PathVariable UUID id) {

        return toResponse(returnOrderRepository.findById(id).orElseThrow());

    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ReturnResponse create(@Valid @RequestBody CreateReturnRequest request) {

        List<ReturnService.ReturnLineInput> lines = request.lines().stream()

                .map(l -> new ReturnService.ReturnLineInput(l.salesOrderLineId(), l.quantityExpected()))

                .toList();

        return toResponse(returnService.create(request.salesOrderId(), lines));

    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ReturnResponse approve(@PathVariable UUID id) {

        return toResponse(returnService.approve(id));

    }

    @PostMapping("/{id}/review/approve-with-label")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ReturnResponse approveWithLabel(@PathVariable UUID id) {
        return toResponse(returnService.approveWithLabel(id));
    }

    @PostMapping("/{id}/review/approve-without-label")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ReturnResponse approveWithoutLabel(@PathVariable UUID id) {
        return toResponse(returnService.approveWithoutLabel(id));
    }

    @PostMapping("/{id}/review/deny")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ReturnResponse denyReview(@PathVariable UUID id) {
        return toResponse(returnService.denyAndClose(id));
    }

    @PutMapping("/{returnId}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ReturnLineResponse updateDisposition(@PathVariable UUID returnId,

                                                @PathVariable UUID lineId,

                                                @Valid @RequestBody UpdateDispositionRequest request) {

        ReturnLine line = returnService.setDisposition(lineId, request.disposition());

        return toLineResponse(line);

    }

    @PostMapping("/{returnId}/lines/{lineId}/receive")

    public ReturnLineResponse receiveLine(@PathVariable UUID returnId,

                                          @PathVariable UUID lineId,

                                          @Valid @RequestBody ReceiveLineRequest request) {

        ReturnLine line = returnService.receiveIncrement(lineId, request.quantity(), request.locationId());

        return toLineResponse(line);

    }

    @PostMapping("/lines/{lineId}/receipt")

    public ReturnLineResponse processReceipt(@PathVariable UUID lineId,

                                             @Valid @RequestBody ProcessReceiptRequest request) {

        ReturnLine line = returnService.processReceipt(lineId, request.locationId(), request.disposition());

        return toLineResponse(line);

    }

    @PostMapping("/lines/{lineId}/release-from-quarantine")

    public ReturnLineResponse releaseFromQuarantine(@PathVariable UUID lineId,

                                                    @Valid @RequestBody ReleaseQuarantineRequest request) {

        ReturnLine line = returnService.releaseFromQuarantine(lineId, request.disposition());

        return toLineResponse(line);

    }

    private ReturnResponse toResponse(ReturnOrder returnOrder) {

        Map<UUID, SalesOrder> orders = salesOrderRepository.findAll().stream()

                .collect(Collectors.toMap(SalesOrder::getId, o -> o, (a, b) -> a));

        Map<UUID, String> customerNames = customerRepository.findAll().stream()

                .collect(Collectors.toMap(Customer::getId, Customer::getName, (a, b) -> a));

        SalesOrder salesOrder = orders.get(returnOrder.getSalesOrderId());

        String salesOrderNumber = salesOrder != null ? salesOrder.getNumber() : null;

        String customerName = salesOrder != null

                ? customerNames.getOrDefault(salesOrder.getCustomerId(), null)

                : null;

        List<ReturnLineResponse> lines = returnLineRepository.findByReturnId(returnOrder.getId()).stream()
                .map(this::toLineResponse)
                .toList();
        List<String> evidenceUrls = lines.stream()
                .map(ReturnLineResponse::evidenceUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();

        return new ReturnResponse(
                returnOrder.getId(),
                returnOrder.getSalesOrderId(),
                salesOrderNumber,
                customerName,
                returnOrder.getNumber(),
                returnOrder.getStatus(),
                returnOrder.getReasonCode(),
                returnOrder.getReturnLabelUrl(),
                returnOrder.getEstimatedLabelCost(),
                returnOrder.getLabelPurchaseMode(),
                evidenceUrls,
                lines,
                returnOrder.getCreatedAt());
    }

    private ReturnLineResponse toLineResponse(ReturnLine line) {

        SalesOrderLine sol = salesOrderLineRepository.findById(line.getSalesOrderLineId()).orElse(null);

        String sku = null;

        String productName = null;

        String putawayTarget = null;

        if (sol != null) {

            ProductVariant variant = variantRepository.findById(sol.getVariantId()).orElse(null);

            if (variant != null) {

                sku = variant.getSku();

                productName = productRepository.findById(variant.getProductId())

                        .map(Product::getName)

                        .orElse(variant.getSku());

                putawayTarget = scanService.resolvePutawayPath(variant);

            }

        }

        String evidenceUrl = line.getMediaObjectId() != null
                ? mediaUploadService.contentPath(line.getMediaObjectId())
                : null;
        return new ReturnLineResponse(
                line.getId(),
                line.getReturnId(),
                line.getSalesOrderLineId(),
                sku,
                productName,
                line.getQuantityExpected(),
                line.getQuantityReceived(),
                line.getDisposition(),
                putawayTarget,
                line.getReasonCode(),
                line.getMediaObjectId(),
                evidenceUrl);
    }

    public record CreateReturnRequest(

            @NotNull UUID salesOrderId,

            @NotNull List<CreateReturnLineRequest> lines

    ) {

    }

    public record CreateReturnLineRequest(

            @NotNull UUID salesOrderLineId,

            @NotNull @Positive BigDecimal quantityExpected

    ) {

    }

    public record UpdateDispositionRequest(@NotBlank String disposition) {

    }

    public record ReceiveLineRequest(BigDecimal quantity, UUID locationId) {

    }

    public record ProcessReceiptRequest(

            @NotNull UUID locationId,

            @NotBlank String disposition

    ) {

    }

    public record ReleaseQuarantineRequest(@NotBlank String disposition) {

    }

}

