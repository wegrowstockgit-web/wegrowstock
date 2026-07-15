package com.invsys.api;

import com.invsys.domain.PurchaseOrder;
import com.invsys.service.AuthenticatedSupplierPortalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Authenticated vendor ASN portal (role SUPPLIER), distinct from magic-link
 * {@code /api/v1/public/supplier-portal/**}.
 */
@RestController
@RequestMapping("/api/v1/supplier-portal")
@PreAuthorize("hasRole('SUPPLIER')")
public class SupplierPortalController {

    private final AuthenticatedSupplierPortalService supplierPortalService;

    public SupplierPortalController(AuthenticatedSupplierPortalService supplierPortalService) {
        this.supplierPortalService = supplierPortalService;
    }

    @GetMapping("/purchase-orders")
    public List<PurchaseOrder> purchaseOrders() {
        return supplierPortalService.openPurchaseOrders();
    }
}
