package com.invsys.api;

import com.invsys.integration.easypost.RateShoppingService;
import com.invsys.modules.fulfillment.domain.Shipment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments")
public class ShipmentRateShoppingController {

    private final RateShoppingService rateShoppingService;

    public ShipmentRateShoppingController(RateShoppingService rateShoppingService) {
        this.rateShoppingService = rateShoppingService;
    }

    @PostMapping("/rate-shop")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public RateShoppingService.RateQuoteResponse rateShop(@Valid @RequestBody RateShopRequest request) {
        return rateShoppingService.shopRates(request.salesOrderId(), request.cartonId());
    }

    @PostMapping("/auto-buy-label")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public Shipment autoBuyLabel(@Valid @RequestBody RateShopRequest request) {
        return rateShoppingService.buyCheapestLabel(request.salesOrderId(), request.cartonId());
    }

    public record RateShopRequest(
            @NotNull UUID salesOrderId,
            UUID cartonId
    ) {
    }
}
