package com.invsys.modules.fintech.api;

import com.invsys.core.security.RequireModule;
import com.invsys.domain.subscription.AppModule;
import com.invsys.modules.fintech.domain.CapitalCreditLine;
import com.invsys.modules.fintech.domain.FactoredInvoice;
import com.invsys.modules.fintech.service.FintechUnderwritingService;
import com.invsys.service.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fintech")
@PreAuthorize("hasRole('OWNER')")
@RequireModule(AppModule.FINTECH)
public class FintechController {

    private final FintechUnderwritingService fintechService;
    private final IdempotencyService idempotencyService;

    public FintechController(FintechUnderwritingService fintechService,
                               IdempotencyService idempotencyService) {
        this.fintechService = fintechService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/dashboard")
    public FintechDashboardResponse dashboard() {
        FintechUnderwritingService.FintechDashboard dash = fintechService.dashboard();
        CapitalCreditLine line = dash.creditLine();
        FintechUnderwritingService.UnderwritingMetrics metrics = dash.underwriting();
        return new FintechDashboardResponse(
                new CreditLineResponse(
                        line.getCreditLimit(),
                        line.getOutstandingBalance(),
                        line.getInterestRateApr(),
                        line.getUtilizationStatus()),
                dash.utilizationPercent(),
                new UnderwritingResponse(
                        metrics.gmv30d(),
                        metrics.gmv90d(),
                        metrics.dsoDays(),
                        metrics.avgInvoiceAgeDays(),
                        metrics.paymentVelocityScore(),
                        metrics.eligibleFactoringLimit()),
                dash.eligibleInvoices().stream()
                        .map(e -> new EligibleInvoiceResponse(e.invoiceId(), e.number(), e.total(), e.advanceAmount()))
                        .toList()
        );
    }

    @PostMapping("/factoring/{invoiceId}/request")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> requestFactoring(
            @PathVariable UUID invoiceId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return executeFactoring(invoiceId, requireIdempotencyKey(idempotencyKey));
    }

    @PostMapping("/factor")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> factor(
            @Valid @RequestBody FactorRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return executeFactoring(body.invoiceId(), requireIdempotencyKey(idempotencyKey));
    }

    private ResponseEntity<?> executeFactoring(UUID targetInvoiceId, String idempotencyKey) {
        var cached = idempotencyService.find(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.status(cached.get().status()).body(cached.get().body());
        }

        FactoredInvoice factored = fintechService.requestFactoring(targetInvoiceId);
        FactoringResponse response = new FactoringResponse(
                factored.getId(),
                factored.getInvoiceId(),
                factored.getAdvanceRate(),
                factored.getDiscountFeePercent(),
                factored.getFundingStatus(),
                factored.getEscrowPayoutRef());

        idempotencyService.store(idempotencyKey, targetInvoiceId.toString(), HttpStatus.OK.value(), toMap(response));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/capital/drawdown")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> capitalDrawdown(
            @Valid @RequestBody DrawdownRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return executeDrawdown(request, requireIdempotencyKey(idempotencyKey));
    }

    @PostMapping("/drawdown")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> drawdown(
            @Valid @RequestBody DrawdownRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return executeDrawdown(request, requireIdempotencyKey(idempotencyKey));
    }

    private ResponseEntity<?> executeDrawdown(DrawdownRequest request, String idempotencyKey) {
        var cached = idempotencyService.find(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.status(cached.get().status()).body(cached.get().body());
        }

        CapitalCreditLine line = fintechService.drawCapital(request.amount());
        CreditLineResponse response = new CreditLineResponse(
                line.getCreditLimit(),
                line.getOutstandingBalance(),
                line.getInterestRateApr(),
                line.getUtilizationStatus());

        idempotencyService.store(idempotencyKey, request.amount().toPlainString(), HttpStatus.OK.value(), toMap(response));
        return ResponseEntity.ok(response);
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.invsys.core.common.ApiException(
                    HttpStatus.BAD_REQUEST, "IDEMPOTENCY_REQUIRED", "Idempotency-Key header is required");
        }
        return idempotencyKey.trim();
    }

    private Map<String, Object> toMap(FactoringResponse r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.id().toString());
        map.put("invoiceId", r.invoiceId().toString());
        map.put("advanceRate", r.advanceRate());
        map.put("discountFeePercent", r.discountFeePercent());
        map.put("fundingStatus", r.fundingStatus());
        map.put("escrowPayoutRef", r.escrowPayoutRef());
        return map;
    }

    private Map<String, Object> toMap(CreditLineResponse r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("creditLimit", r.creditLimit());
        map.put("outstandingBalance", r.outstandingBalance());
        map.put("interestRateApr", r.interestRateApr());
        map.put("utilizationStatus", r.utilizationStatus());
        return map;
    }

    public record FactorRequest(@NotNull UUID invoiceId) {
    }

    public record DrawdownRequest(@NotNull @Positive BigDecimal amount) {
    }

    public record FintechDashboardResponse(
            CreditLineResponse creditLine,
            BigDecimal utilizationPercent,
            UnderwritingResponse underwriting,
            List<EligibleInvoiceResponse> eligibleInvoices
    ) {
    }

    public record UnderwritingResponse(
            BigDecimal gmv30d,
            BigDecimal gmv90d,
            BigDecimal dsoDays,
            BigDecimal avgInvoiceAgeDays,
            BigDecimal paymentVelocityScore,
            BigDecimal eligibleFactoringLimit
    ) {
    }

    public record CreditLineResponse(
            BigDecimal creditLimit,
            BigDecimal outstandingBalance,
            BigDecimal interestRateApr,
            String utilizationStatus
    ) {
    }

    public record EligibleInvoiceResponse(
            UUID invoiceId,
            String number,
            BigDecimal total,
            BigDecimal advanceAmount
    ) {
    }

    public record FactoringResponse(
            UUID id,
            UUID invoiceId,
            BigDecimal advanceRate,
            BigDecimal discountFeePercent,
            String fundingStatus,
            String escrowPayoutRef
    ) {
    }
}
