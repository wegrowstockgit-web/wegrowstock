package com.invsys.api;

import com.invsys.service.PortalRmaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/v1/showroom/returns")
@PreAuthorize("hasRole('B2B_CUSTOMER')")
public class ShowroomReturnsController {

    private final PortalRmaService portalRmaService;

    public ShowroomReturnsController(PortalRmaService portalRmaService) {
        this.portalRmaService = portalRmaService;
    }

    @GetMapping("/eligible/{salesOrderId}")
    public List<PortalRmaService.EligibleLine> eligible(@PathVariable UUID salesOrderId) {
        return portalRmaService.eligibleLines(salesOrderId);
    }

    @GetMapping("/{id}")
    public PortalRmaResponse get(@PathVariable UUID id) {
        return PortalRmaResponse.from(portalRmaService.getPortalReturn(id));
    }

    @PostMapping
    public PortalRmaResponse create(@Valid @RequestBody CreatePortalRmaRequest request) {
        List<PortalRmaService.PortalRmaLineInput> lines = request.lines().stream()
                .map(l -> new PortalRmaService.PortalRmaLineInput(
                        l.salesOrderLineId(), l.quantity(), l.mediaObjectId()))
                .toList();
        PortalRmaService.PortalRmaResult result = portalRmaService.createPortalReturn(
                request.salesOrderId(), request.reasonCode(), lines);
        return PortalRmaResponse.from(result);
    }

    public record CreatePortalRmaRequest(
            @NotNull UUID salesOrderId,
            @NotBlank String reasonCode,
            @NotNull List<PortalRmaLineRequest> lines
    ) {
    }

    public record PortalRmaLineRequest(
            @NotNull UUID salesOrderLineId,
            @NotNull @Positive BigDecimal quantity,
            UUID mediaObjectId
    ) {
    }

    public record PortalRmaResponse(
            UUID id,
            String number,
            String status,
            String reviewReason,
            String returnLabelUrl,
            BigDecimal estimatedLabelCost,
            BigDecimal merchandiseValue,
            String labelPurchaseMode,
            String shippingInstruction
    ) {
        static PortalRmaResponse from(PortalRmaService.PortalRmaResult result) {
            return new PortalRmaResponse(
                    result.id(),
                    result.number(),
                    result.status(),
                    result.reviewReason(),
                    result.returnLabelUrl(),
                    result.estimatedLabelCost(),
                    result.merchandiseValue(),
                    result.labelPurchaseMode(),
                    result.shippingInstruction());
        }
    }
}
