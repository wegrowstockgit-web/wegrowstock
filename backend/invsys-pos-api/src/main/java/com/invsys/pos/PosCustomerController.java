package com.invsys.pos;

import com.invsys.core.security.RequireModule;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.subscription.AppModule;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.pos.dto.PosCustomerResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pos")
@RequireModule(AppModule.RETAIL_POS)
public class PosCustomerController {

    private final CustomerRepository customerRepository;

    public PosCustomerController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @GetMapping("/customers")
    public List<PosCustomerResponse> customers() {
        return customerRepository.findByTenantIdOrderByNameAsc(TenantContext.requireTenantId()).stream()
                .map(row -> new PosCustomerResponse(row.getId(), row.getName(), row.getEmail()))
                .toList();
    }
}
