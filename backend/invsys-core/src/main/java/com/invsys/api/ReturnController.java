package com.invsys.api;

import com.invsys.api.dto.ReturnLineResponse;

import com.invsys.api.dto.ReturnResponse;

import com.invsys.modules.sales.domain.Customer;

import com.invsys.modules.catalog.domain.Product;

import com.invsys.modules.catalog.domain.ProductVariant;

import com.invsys.domain.ReturnLine;

import com.invsys.domain.ReturnOrder;

import com.invsys.modules.sales.domain.SalesOrder;

import com.invsys.modules.sales.domain.SalesOrderLine;

import com.invsys.modules.sales.repository.CustomerRepository;

import com.invsys.modules.catalog.repository.ProductRepository;

import com.invsys.modules.catalog.repository.ProductVariantRepository;

import com.invsys.repository.ReturnLineRepository;

import com.invsys.repository.ReturnOrderRepository;

import com.invsys.modules.sales.repository.SalesOrderLineRepository;

import com.invsys.modules.sales.repository.SalesOrderRepository;

import com.invsys.media.MediaUploadService;

import com.invsys.core.common.PageResponse;

import com.invsys.domain.RtvOrder;

import com.invsys.modules.sales.domain.Invoice;

import com.invsys.modules.sales.repository.InvoiceRepository;

import com.invsys.repository.RtvOrderRepository;

import com.invsys.service.ReturnService;

import com.invsys.service.ReturnToVendorService;

import com.invsys.service.ScanService;

import com.invsys.core.tenancy.TenantContext;

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
    private final InvoiceRepository invoiceRepository;
    private final RtvOrderRepository rtvOrderRepository;

    public ReturnController(ReturnOrderRepository returnOrderRepository,
                            ReturnLineRepository returnLineRepository,
                            ReturnService returnService,
                            SalesOrderRepository salesOrderRepository,
                            SalesOrderLineRepository salesOrderLineRepository,
                            CustomerRepository customerRepository,
                            ProductVariantRepository variantRepository,
                            ProductRepository productRepository,
                            ScanService scanService,
                            MediaUploadService mediaUploadService,
                            InvoiceRepository invoiceRepository,
                            RtvOrderRepository rtvOrderRepository) {
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
        this.invoiceRepository = invoiceRepository;
        this.rtvOrderRepository = rtvOrderRepository;
    }

    @GetMapping
    public Object list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String cursor,
                       @RequestParam(required = false) Integer limit,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size) {
        UUID tenantId = TenantContext.requireTenantId();
        List<ReturnOrder> orders = status != null && !status.isBlank()
                ? returnOrderRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : returnOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        if (page != null || size != null) {
            int pageNumber = page == null || page < 1 ? 1 : page;
            int pageSize = size == null ? 25 : Math.min(Math.max(size, 1), 100);
            int start = (pageNumber - 1) * pageSize;
            int end = Math.min(start + pageSize, orders.size());
            List<ReturnOrder> slice = start >= orders.size() ? List.of() : orders.subList(start, end);
            int totalPages = orders.isEmpty() ? 0 : (int) Math.ceil(orders.size() / (double) pageSize);
            return new PageResponse<>(
                    slice.stream().map(this::toResponse).toList(),
                    null,
                    end < orders.size(),
                    (long) orders.size(),
                    totalPages,
                    pageNumber,
                    pageSize
            );
        }
        if (limit == null) {
            return orders.stream().map(this::toResponse).toList();
        }
        int cursorSize = Math.min(Math.max(limit, 1), 100);
        int start = 0;
        if (cursor != null && !cursor.isBlank()) {
            for (int i = 0; i < orders.size(); i++) {
                if (cursor.equals(orders.get(i).getId().toString())) {
                    start = i + 1;
                    break;
                }
            }
        }
        int end = Math.min(start + cursorSize, orders.size());
        List<ReturnOrder> cursorPage = start >= orders.size() ? List.of() : orders.subList(start, end);
        boolean hasMore = end < orders.size();
        String nextCursor = hasMore && !cursorPage.isEmpty() ? cursorPage.getLast().getId().toString() : null;
        return new PageResponse<>(cursorPage.stream().map(this::toResponse).toList(), nextCursor, hasMore);
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
                .map(l -> new ReturnService.ReturnLineInput(
                        l.salesOrderLineId(), l.quantityExpected(), l.reasonCode()))
                .toList();
        boolean generateLabel = Boolean.TRUE.equals(request.generateLabel());
        return toResponse(returnService.create(
                request.salesOrderId(), lines, request.resolutionType(), generateLabel));

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

        ReturnLine line = returnService.setDisposition(
                lineId, request.disposition(), request.restockLocationId(), request.restockingFeePct());

        return toLineResponse(line);

    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public ReturnResponse complete(@PathVariable UUID id) {
        return toResponse(returnService.completeDisposition(id));
    }

    @PostMapping("/{id}/escalate-rtv")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public Map<String, Object> escalateRtv(@PathVariable UUID id) {
        ReturnToVendorService.RtvDetail detail = returnService.escalateToRtv(id);
        return Map.of(
                "rtvOrderId", detail.order().getId(),
                "rtvNumber", detail.order().getNumber(),
                "status", detail.order().getStatus());
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

        BigDecimal estimatedValue = BigDecimal.ZERO;
        int itemCount = 0;
        for (ReturnLineResponse line : lines) {
            SalesOrderLine sol = salesOrderLineRepository.findById(line.salesOrderLineId()).orElse(null);
            BigDecimal qty = line.quantityExpected() != null ? line.quantityExpected() : BigDecimal.ZERO;
            itemCount += qty.intValue();
            if (sol != null && sol.getUnitPrice() != null) {
                estimatedValue = estimatedValue.add(sol.getUnitPrice().multiply(qty));
            }
        }
        String creditMemoNumber = null;
        if (returnOrder.getCreditMemoId() != null) {
            creditMemoNumber = invoiceRepository.findById(returnOrder.getCreditMemoId())
                    .map(Invoice::getNumber)
                    .orElse(null);
        }
        String rtvNumber = null;
        if (returnOrder.getRtvOrderId() != null) {
            rtvNumber = rtvOrderRepository.findById(returnOrder.getRtvOrderId())
                    .map(RtvOrder::getNumber)
                    .orElse(null);
        }

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
                returnOrder.getCreatedAt(),
                estimatedValue,
                itemCount,
                returnOrder.getTrackingNumber(),
                returnOrder.getResolutionType(),
                returnOrder.getCreditMemoId(),
                creditMemoNumber,
                returnOrder.getRtvOrderId(),
                rtvNumber);
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
                evidenceUrl,
                line.getRestockLocationId(),
                line.getRestockingFeePct());
    }

    public record CreateReturnRequest(

            @NotNull UUID salesOrderId,

            @NotNull List<CreateReturnLineRequest> lines,

            String resolutionType,

            Boolean generateLabel

    ) {

    }

    public record CreateReturnLineRequest(

            @NotNull UUID salesOrderLineId,

            @NotNull @Positive BigDecimal quantityExpected,

            String reasonCode

    ) {

    }

    public record UpdateDispositionRequest(
            @NotBlank String disposition,
            UUID restockLocationId,
            BigDecimal restockingFeePct
    ) {

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

