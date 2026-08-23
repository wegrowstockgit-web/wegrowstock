package com.invsys.modules.sales.api;

import com.invsys.modules.sales.service.CustomerMasterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CustomerMasterController {

    private final CustomerMasterService customerMasterService;

    public CustomerMasterController(CustomerMasterService customerMasterService) {
        this.customerMasterService = customerMasterService;
    }

    @GetMapping("/customers/summary")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public CustomerMasterService.CustomerSummary summary() {
        return customerMasterService.summary();
    }

    @GetMapping("/customers/price-tiers")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public List<CustomerMasterService.PriceTierItem> priceTiers(@RequestParam(required = false) String search) {
        return customerMasterService.priceTiers(search);
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public CustomerMasterService.CustomerListItem get(@PathVariable UUID id) {
        return customerMasterService.get(id);
    }

    @PostMapping("/customers/{id}/portal-invite")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public CustomerMasterService.CustomerListItem portalInvite(@PathVariable UUID id) {
        return customerMasterService.invitePortal(id);
    }

    @PostMapping("/customers/{id}/credit-hold")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','FINANCE_ADMIN')")
    public CustomerMasterService.CustomerListItem creditHold(@PathVariable UUID id) {
        return customerMasterService.placeOnHold(id);
    }
}
