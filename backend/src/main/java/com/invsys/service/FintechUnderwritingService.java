package com.invsys.service;

import com.invsys.billing.CapitalGateway;
import com.invsys.common.ApiException;
import com.invsys.domain.CapitalCreditLine;
import com.invsys.domain.FactoredInvoice;
import com.invsys.domain.Invoice;
import com.invsys.domain.Payment;
import com.invsys.repository.CapitalCreditLineRepository;
import com.invsys.repository.FactoredInvoiceRepository;
import com.invsys.repository.InvoiceRepository;
import com.invsys.repository.PaymentRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FintechUnderwritingService {

    private static final BigDecimal DEFAULT_ADVANCE_RATE = new BigDecimal("85.00");
    private static final BigDecimal DEFAULT_DISCOUNT_FEE = new BigDecimal("2.50");
    private static final int GMV_LOOKBACK_DAYS = 30;
    private static final int PAYMENT_LOOKBACK_DAYS = 90;

    private final InvoiceRepository invoiceRepository;
    private final FactoredInvoiceRepository factoredInvoiceRepository;
    private final CapitalCreditLineRepository creditLineRepository;
    private final PaymentRepository paymentRepository;
    private final CapitalGateway capitalGateway;

    public FintechUnderwritingService(InvoiceRepository invoiceRepository,
                                      FactoredInvoiceRepository factoredInvoiceRepository,
                                      CapitalCreditLineRepository creditLineRepository,
                                      PaymentRepository paymentRepository,
                                      CapitalGateway capitalGateway) {
        this.invoiceRepository = invoiceRepository;
        this.factoredInvoiceRepository = factoredInvoiceRepository;
        this.creditLineRepository = creditLineRepository;
        this.paymentRepository = paymentRepository;
        this.capitalGateway = capitalGateway;
    }

    public FintechDashboard dashboard() {
        UUID tenantId = TenantContext.requireTenantId();
        final UnderwritingMetrics baseMetrics = computeMetrics(tenantId);

        CapitalCreditLine creditLine = creditLineRepository.findByTenantId(tenantId)
                .orElseGet(() -> provisionCreditLine(tenantId, baseMetrics));

        List<EligibleInvoice> eligible = new ArrayList<>();
        BigDecimal factoringLimit = BigDecimal.ZERO;
        for (Invoice invoice : invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)) {
            if (isFactoringEligible(invoice)) {
                BigDecimal advance = invoice.getTotal()
                        .multiply(DEFAULT_ADVANCE_RATE)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                eligible.add(new EligibleInvoice(invoice.getId(), invoice.getNumber(), invoice.getTotal(), advance));
                factoringLimit = factoringLimit.add(advance);
            }
        }

        UnderwritingMetrics metrics = new UnderwritingMetrics(
                baseMetrics.gmv30d(),
                baseMetrics.gmv90d(),
                baseMetrics.dsoDays(),
                baseMetrics.avgInvoiceAgeDays(),
                baseMetrics.paymentVelocityScore(),
                factoringLimit.setScale(2, RoundingMode.HALF_UP));

        BigDecimal utilization = creditLine.getCreditLimit().signum() == 0
                ? BigDecimal.ZERO
                : creditLine.getOutstandingBalance()
                        .divide(creditLine.getCreditLimit(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

        return new FintechDashboard(creditLine, eligible, utilization, metrics);
    }

    @Transactional
    public FactoredInvoice requestFactoring(UUID invoiceId) {
        UUID tenantId = TenantContext.requireTenantId();
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invoice not found"));

        if (!isFactoringEligible(invoice)) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ELIGIBLE", "Invoice is not eligible for factoring");
        }

        FactoredInvoice factored = factoredInvoiceRepository.findByTenantIdAndInvoiceId(tenantId, invoiceId)
                .orElseGet(() -> {
                    FactoredInvoice created = new FactoredInvoice();
                    created.setTenantId(tenantId);
                    created.setInvoiceId(invoiceId);
                    created.setAdvanceRate(DEFAULT_ADVANCE_RATE);
                    created.setDiscountFeePercent(DEFAULT_DISCOUNT_FEE);
                    created.setFundingStatus("ELIGIBLE");
                    return created;
                });

        if ("FUNDED".equals(factored.getFundingStatus())) {
            return factored;
        }

        BigDecimal advanceAmount = invoice.getTotal()
                .multiply(factored.getAdvanceRate())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        CapitalGateway.FactoringPayoutResult payout = capitalGateway.fundFactoring(tenantId, invoiceId, advanceAmount);
        factored.setFundingStatus("FUNDED");
        factored.setEscrowPayoutRef(payout.escrowPayoutRef());

        // Track advanced capital as outstanding so Stripe clearance can amortize payback
        CapitalCreditLine line = creditLineRepository.findByTenantId(tenantId)
                .orElseGet(() -> provisionCreditLine(tenantId, computeMetrics(tenantId)));
        line.setOutstandingBalance(line.getOutstandingBalance().add(advanceAmount));
        line.setUtilizationStatus("DRAWN");
        creditLineRepository.save(line);

        return factoredInvoiceRepository.save(factored);
    }

    /**
     * On inbound Stripe clearance of a factored invoice, siphon fractional amortization
     * from the capital credit line outstanding balance prior to net merchant payout.
     *
     * @return amount applied to outstanding balance (zero if not factored / already settled)
     */
    @Transactional
    public BigDecimal applyFactoringPayback(UUID invoiceId, BigDecimal settlementAmount) {
        UUID tenantId = TenantContext.requireTenantId();
        FactoredInvoice factored = factoredInvoiceRepository.findByTenantIdAndInvoiceId(tenantId, invoiceId)
                .orElse(null);
        if (factored == null || !"FUNDED".equals(factored.getFundingStatus())) {
            return BigDecimal.ZERO;
        }

        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            return BigDecimal.ZERO;
        }

        // Fractional payback ≈ advance principal + discount fee on the settled amount
        BigDecimal advancePrincipal = settlementAmount
                .multiply(factored.getAdvanceRate())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal fee = settlementAmount
                .multiply(factored.getDiscountFeePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal payback = advancePrincipal.add(fee);

        CapitalCreditLine line = creditLineRepository.findByTenantId(tenantId).orElse(null);
        if (line != null && line.getOutstandingBalance().signum() > 0) {
            BigDecimal applied = payback.min(line.getOutstandingBalance());
            line.setOutstandingBalance(line.getOutstandingBalance().subtract(applied).max(BigDecimal.ZERO));
            line.setUtilizationStatus(line.getOutstandingBalance().signum() > 0 ? "DRAWN" : "AVAILABLE");
            creditLineRepository.save(line);
            factored.setFundingStatus("SETTLED");
            factoredInvoiceRepository.save(factored);
            return applied;
        }

        // No drawn capital — still mark factoring settled after customer clearance
        factored.setFundingStatus("SETTLED");
        factoredInvoiceRepository.save(factored);
        return payback;
    }

    @Transactional
    public CapitalCreditLine drawCapital(BigDecimal amount) {
        UUID tenantId = TenantContext.requireTenantId();
        CapitalCreditLine line = creditLineRepository.findByTenantId(tenantId)
                .orElseGet(() -> provisionCreditLine(tenantId, computeMetrics(tenantId)));

        BigDecimal available = line.getCreditLimit().subtract(line.getOutstandingBalance());
        if (amount.compareTo(available) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "LIMIT_EXCEEDED", "Draw exceeds available credit");
        }

        capitalGateway.createDrawdown(tenantId, amount);
        line.setOutstandingBalance(line.getOutstandingBalance().add(amount));
        line.setUtilizationStatus(line.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0 ? "DRAWN" : "AVAILABLE");
        return creditLineRepository.save(line);
    }

    @Transactional
    public CapitalCreditLine provisionCreditLine(UUID tenantId) {
        return provisionCreditLine(tenantId, computeMetrics(tenantId));
    }

    @Transactional
    public CapitalCreditLine provisionCreditLine(UUID tenantId, UnderwritingMetrics metrics) {
        // Dynamic limit from GMV30d cash flow with DSO penalty / payment velocity boost.
        BigDecimal baseLimit = metrics.gmv30d().multiply(new BigDecimal("0.25"));
        BigDecimal velocityBoost = metrics.paymentVelocityScore()
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("0.10"));
        BigDecimal dsoPenalty = metrics.dsoDays()
                .divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE)
                .multiply(new BigDecimal("0.25"));

        BigDecimal multiplier = BigDecimal.ONE.add(velocityBoost).subtract(dsoPenalty).max(new BigDecimal("0.40"));
        BigDecimal limit = baseLimit.multiply(multiplier).max(new BigDecimal("10000")).setScale(2, RoundingMode.HALF_UP);

        CapitalCreditLine line = creditLineRepository.findByTenantId(tenantId).orElseGet(CapitalCreditLine::new);
        line.setTenantId(tenantId);
        line.setCreditLimit(limit);
        if (line.getOutstandingBalance() == null) {
            line.setOutstandingBalance(BigDecimal.ZERO);
        }
        if (line.getInterestRateApr() == null) {
            line.setInterestRateApr(new BigDecimal("12.00"));
        }
        if (line.getUtilizationStatus() == null) {
            line.setUtilizationStatus("AVAILABLE");
        }
        return creditLineRepository.save(line);
    }

    /**
     * Continuous underwriting refresh — recalculates credit limits from live GMV30d / DSO.
     */
    @Transactional
    public CapitalCreditLine refreshUnderwriting(UUID tenantId) {
        return provisionCreditLine(tenantId, computeMetrics(tenantId));
    }

    public UnderwritingMetrics computeMetrics(UUID tenantId) {
        Instant gmvCutoff = Instant.now().minus(GMV_LOOKBACK_DAYS, ChronoUnit.DAYS);
        Instant paymentCutoff = Instant.now().minus(PAYMENT_LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<Invoice> invoices = invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        BigDecimal gmv30d = invoices.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().isAfter(gmvCutoff))
                .filter(i -> List.of("OPEN", "PARTIALLY_PAID", "PAID").contains(i.getStatus()))
                .map(Invoice::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant gmv90Cutoff = Instant.now().minus(PAYMENT_LOOKBACK_DAYS, ChronoUnit.DAYS);
        BigDecimal gmv90d = invoices.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().isAfter(gmv90Cutoff))
                .filter(i -> List.of("OPEN", "PARTIALLY_PAID", "PAID").contains(i.getStatus()))
                .map(Invoice::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Invoice> openInvoices = invoices.stream()
                .filter(i -> List.of("OPEN", "PARTIALLY_PAID").contains(i.getStatus()))
                .toList();

        BigDecimal openAr = openInvoices.stream()
                .map(Invoice::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // DSO ≈ (open AR / GMV30d) * 30
        BigDecimal dsoDays = BigDecimal.ZERO;
        if (gmv30d.signum() > 0) {
            dsoDays = openAr
                    .divide(gmv30d, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(GMV_LOOKBACK_DAYS))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        double avgAgeDays = openInvoices.isEmpty() ? 0.0 : openInvoices.stream()
                .mapToDouble(i -> {
                    Instant anchor = i.getCreatedAt() != null ? i.getCreatedAt() : Instant.now();
                    return ChronoUnit.DAYS.between(anchor, Instant.now());
                })
                .average()
                .orElse(0.0);

        BigDecimal payments90d = paymentRepository.findByTenantId(tenantId).stream()
                .filter(p -> p.getSettledAt() != null && p.getSettledAt().isAfter(paymentCutoff))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paymentVelocity = payments90d.divide(new BigDecimal(PAYMENT_LOOKBACK_DAYS), 4, RoundingMode.HALF_UP);
        BigDecimal invoiceVelocity = gmv90d.divide(new BigDecimal(PAYMENT_LOOKBACK_DAYS), 4, RoundingMode.HALF_UP);

        BigDecimal velocityScore = BigDecimal.ZERO;
        if (invoiceVelocity.signum() > 0) {
            velocityScore = paymentVelocity
                    .divide(invoiceVelocity, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .min(new BigDecimal("100"))
                    .max(BigDecimal.ZERO);
        } else if (payments90d.signum() > 0) {
            velocityScore = new BigDecimal("75");
        }

        return new UnderwritingMetrics(
                gmv30d.setScale(2, RoundingMode.HALF_UP),
                gmv90d.setScale(2, RoundingMode.HALF_UP),
                dsoDays,
                BigDecimal.valueOf(Math.max(0, avgAgeDays)).setScale(1, RoundingMode.HALF_UP),
                velocityScore.setScale(1, RoundingMode.HALF_UP),
                BigDecimal.ZERO);
    }

    private boolean isFactoringEligible(Invoice invoice) {
        if (!"OPEN".equals(invoice.getStatus())) {
            return false;
        }
        if (invoice.getDueAt() == null) {
            return true;
        }
        return invoice.getDueAt().isAfter(Instant.now().minus(90, ChronoUnit.DAYS));
    }

    public record EligibleInvoice(UUID invoiceId, String number, BigDecimal total, BigDecimal advanceAmount) {
    }

    public record UnderwritingMetrics(
            BigDecimal gmv30d,
            BigDecimal gmv90d,
            BigDecimal dsoDays,
            BigDecimal avgInvoiceAgeDays,
            BigDecimal paymentVelocityScore,
            BigDecimal eligibleFactoringLimit
    ) {
    }

    public record FintechDashboard(
            CapitalCreditLine creditLine,
            List<EligibleInvoice> eligibleInvoices,
            BigDecimal utilizationPercent,
            UnderwritingMetrics underwriting
    ) {
    }
}
