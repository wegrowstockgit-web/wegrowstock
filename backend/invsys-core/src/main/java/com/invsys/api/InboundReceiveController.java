package com.invsys.api;

import com.invsys.service.InboundReceivingService;
import com.invsys.service.PutawayStrategyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound/receive")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class InboundReceiveController {

    private final InboundReceivingService inboundReceivingService;

    public InboundReceiveController(InboundReceivingService inboundReceivingService) {
        this.inboundReceivingService = inboundReceivingService;
    }

    @GetMapping("/po")
    public InboundReceivingService.InboundPoView lookupPo(@RequestParam String barcode) {
        return inboundReceivingService.lookupPo(barcode);
    }

    @GetMapping("/po/{poId}")
    public InboundReceivingService.InboundPoView getPo(@PathVariable UUID poId) {
        return inboundReceivingService.getPo(poId);
    }

    @PostMapping("/resolve-item")
    public InboundReceivingService.InboundLineMatch resolveItem(@RequestBody ResolveItemBody body) {
        return inboundReceivingService.resolveItem(body.poId(), body.barcode());
    }

    @GetMapping("/putaway-suggestion")
    public PutawayStrategyService.PutawayDirective suggestPutaway(@RequestParam UUID variantId) {
        return inboundReceivingService.suggestPutaway(variantId);
    }

    @PostMapping("/confirm")
    public InboundReceivingService.ConfirmPutawayResult confirm(@RequestBody ConfirmBody body) {
        return inboundReceivingService.confirmPutaway(new InboundReceivingService.ConfirmPutawayRequest(
                body.lineId(),
                body.quantity(),
                body.locationId(),
                body.scannedLocationBarcode(),
                body.lotId(),
                body.tagId()));
    }

    public record ResolveItemBody(UUID poId, String barcode) {
    }

    public record ConfirmBody(
            UUID lineId,
            BigDecimal quantity,
            UUID locationId,
            String scannedLocationBarcode,
            UUID lotId,
            String tagId
    ) {
    }
}
