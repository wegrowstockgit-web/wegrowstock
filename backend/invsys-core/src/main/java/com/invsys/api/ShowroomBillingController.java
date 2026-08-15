package com.invsys.api;

import com.invsys.core.security.RequireModule;
import com.invsys.domain.BillingAccrual;
import com.invsys.domain.BillingSla;
import com.invsys.domain.subscription.AppModule;
import com.invsys.service.BillingAccrualService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/showroom/billing")
@PreAuthorize("hasRole('B2B_CUSTOMER')")
@RequireModule(AppModule.B2B_SHOWROOM)
public class ShowroomBillingController {

    private final BillingAccrualService billingAccrualService;

    public ShowroomBillingController(BillingAccrualService billingAccrualService) {
        this.billingAccrualService = billingAccrualService;
    }

    @GetMapping("/accruals")
    public ShowroomAccrualsResponse accruals() {
        BillingAccrualService.ShowroomAccrualsView view = billingAccrualService.showroomMonthToDate();
        return ShowroomAccrualsResponse.from(view);
    }

    public record ShowroomSlaResponse(
            String storageMode,
            BigDecimal ratePerUnit,
            BigDecimal pickFeePerItem
    ) {
        static ShowroomSlaResponse from(BillingSla sla) {
            if (sla == null) {
                return null;
            }
            return new ShowroomSlaResponse(sla.getStorageMode(), sla.getRatePerUnit(), sla.getPickFeePerItem());
        }
    }

    public record ShowroomAccrualRow(
            UUID id,
            LocalDate accrualDate,
            BigDecimal amount,
            String description,
            String status
    ) {
        static ShowroomAccrualRow from(BillingAccrual accrual) {
            return new ShowroomAccrualRow(
                    accrual.getId(),
                    accrual.getAccrualDate(),
                    accrual.getAmount(),
                    accrual.getDescription(),
                    accrual.getStatus());
        }
    }

    public record ShowroomAccrualsResponse(
            ShowroomSlaResponse sla,
            LocalDate monthStart,
            BigDecimal monthToDateTotal,
            List<ShowroomAccrualRow> accruals
    ) {
        static ShowroomAccrualsResponse from(BillingAccrualService.ShowroomAccrualsView view) {
            return new ShowroomAccrualsResponse(
                    ShowroomSlaResponse.from(view.sla()),
                    view.monthStart(),
                    view.monthToDateTotal(),
                    view.accruals().stream().map(ShowroomAccrualRow::from).toList());
        }
    }
}
