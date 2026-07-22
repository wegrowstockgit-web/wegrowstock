package com.invsys.modules.sales.api;

import com.invsys.domain.BillingAccrual;
import com.invsys.domain.BillingSla;
import com.invsys.service.BillingAccrualService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/billing")
public class CustomerBillingController {

    private final BillingAccrualService billingAccrualService;

    public CustomerBillingController(BillingAccrualService billingAccrualService) {
        this.billingAccrualService = billingAccrualService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','VIEWER')")
    public CustomerBillingResponse getBilling(@PathVariable UUID customerId) {
        BillingAccrualService.CustomerBillingView view = billingAccrualService.officeBilling(customerId);
        return CustomerBillingResponse.from(view);
    }

    @PutMapping("/sla")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SlaResponse upsertSla(@PathVariable UUID customerId,
                                 @Valid @RequestBody UpsertSlaRequest request) {
        BillingSla sla = billingAccrualService.upsertSla(
                customerId, request.storageMode(), request.ratePerUnit(), request.pickFeePerItem());
        return SlaResponse.from(sla);
    }

    public record UpsertSlaRequest(
            @NotBlank String storageMode,
            @DecimalMin("0") BigDecimal ratePerUnit,
            @DecimalMin("0") BigDecimal pickFeePerItem
    ) {
    }

    public record SlaResponse(
            UUID id,
            UUID customerId,
            String storageMode,
            BigDecimal ratePerUnit,
            BigDecimal pickFeePerItem
    ) {
        static SlaResponse from(BillingSla sla) {
            if (sla == null) {
                return null;
            }
            return new SlaResponse(
                    sla.getId(),
                    sla.getCustomerId(),
                    sla.getStorageMode(),
                    sla.getRatePerUnit(),
                    sla.getPickFeePerItem());
        }
    }

    public record AccrualResponse(
            UUID id,
            LocalDate accrualDate,
            BigDecimal amount,
            String description,
            String status
    ) {
        static AccrualResponse from(BillingAccrual accrual) {
            return new AccrualResponse(
                    accrual.getId(),
                    accrual.getAccrualDate(),
                    accrual.getAmount(),
                    accrual.getDescription(),
                    accrual.getStatus());
        }
    }

    public record CustomerBillingResponse(
            SlaResponse sla,
            List<AccrualResponse> unbilledAccruals,
            BigDecimal unbilledTotal
    ) {
        static CustomerBillingResponse from(BillingAccrualService.CustomerBillingView view) {
            return new CustomerBillingResponse(
                    SlaResponse.from(view.sla()),
                    view.unbilledAccruals().stream().map(AccrualResponse::from).toList(),
                    view.unbilledTotal());
        }
    }
}
