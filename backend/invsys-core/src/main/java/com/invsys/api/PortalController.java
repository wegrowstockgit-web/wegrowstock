package com.invsys.api;

import com.invsys.api.dto.PortalCatalogItemResponse;
import com.invsys.api.dto.PortalInvoiceResponse;
import com.invsys.api.dto.PortalOrderResponse;
import com.invsys.service.PortalService;
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
@RequestMapping("/api/v1/portal")
@PreAuthorize("hasRole('B2B_CUSTOMER')")
public class PortalController {

    private final PortalService portalService;

    public PortalController(PortalService portalService) {
        this.portalService = portalService;
    }

    @GetMapping("/catalog")
    public List<PortalCatalogItemResponse> catalog() {
        return portalService.catalog();
    }

    @GetMapping("/orders")
    public List<PortalOrderResponse> orders() {
        return portalService.orders();
    }

    @PostMapping("/orders")
    public PortalOrderResponse createOrder(@Valid @RequestBody CreatePortalOrderRequest request) {
        List<PortalService.PortalOrderLineInput> lines = request.lines().stream()
                .map(l -> new PortalService.PortalOrderLineInput(l.variantId(), l.quantity()))
                .toList();
        return portalService.createOrder(lines, request.customerPoNumber(), request.requestedShipDate());
    }

    @GetMapping("/invoices")
    public List<PortalInvoiceResponse> invoices() {
        return portalService.invoices();
    }

    @GetMapping("/invoices/{invoiceId}/reorder-lines")
    public List<PortalReorderLineResponse> reorderLines(@PathVariable UUID invoiceId) {
        return portalService.reorderLines(invoiceId).stream()
                .map(l -> new PortalReorderLineResponse(l.variantId(), l.sku(), l.name(), l.quantity()))
                .toList();
    }

    @GetMapping("/orders/{orderId}/reorder-lines")
    public List<PortalReorderLineResponse> reorderOrderLines(@PathVariable UUID orderId) {
        return portalService.reorderLinesFromOrder(orderId).stream()
                .map(l -> new PortalReorderLineResponse(l.variantId(), l.sku(), l.name(), l.quantity()))
                .toList();
    }

    @GetMapping("/payment-terms")
    public PaymentTermsResponse paymentTerms() {
        return new PaymentTermsResponse(portalService.paymentTerms());
    }

    @GetMapping("/credit")
    public PortalService.CreditSummary credit() {
        return portalService.creditSummary();
    }

    public record PaymentTermsResponse(String terms) {
    }

    public record CreatePortalOrderRequest(
            @NotNull List<PortalOrderLineRequest> lines,
            String customerPoNumber,
            java.time.Instant requestedShipDate
    ) {
    }

    public record PortalOrderLineRequest(
            @NotNull UUID variantId,
            @NotNull @Positive BigDecimal quantity
    ) {
    }

    public record PortalReorderLineResponse(
            UUID variantId,
            String sku,
            String name,
            BigDecimal quantity
    ) {
    }
}
