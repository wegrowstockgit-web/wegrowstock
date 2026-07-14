package com.invsys.api;

import com.invsys.auth.JwtService;
import com.invsys.service.SupplierPortalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/supplier-portal")
public class PublicSupplierPortalController {

    private final JwtService jwtService;
    private final SupplierPortalService supplierPortalService;

    public PublicSupplierPortalController(JwtService jwtService, SupplierPortalService supplierPortalService) {
        this.jwtService = jwtService;
        this.supplierPortalService = supplierPortalService;
    }

    @GetMapping("/po/{token}")
    public SupplierPortalService.PortalPurchaseOrderView getPo(@PathVariable String token) {
        return supplierPortalService.getPurchaseOrder(jwtService.validateSupplierPortalToken(token));
    }

    @PostMapping("/po/{token}/expected-delivery")
    public SupplierPortalService.PortalPurchaseOrderView updateExpectedDelivery(
            @PathVariable String token,
            @Valid @RequestBody ExpectedDeliveryRequest request) {
        return supplierPortalService.updateExpectedDelivery(
                jwtService.validateSupplierPortalToken(token), request.expectedAt());
    }

    @GetMapping("/po/{token}/labels")
    public List<SupplierPortalService.ReceivingLabel> labels(@PathVariable String token) {
        return supplierPortalService.receivingLabels(jwtService.validateSupplierPortalToken(token));
    }

    public record ExpectedDeliveryRequest(@NotNull Instant expectedAt) {
    }
}
