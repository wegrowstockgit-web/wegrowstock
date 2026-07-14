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
import java.util.Map;
import java.util.UUID;

@Service
public class FintechUnderwritingService {

    private static final BigDecimal DEFAULT_ADVANCE_RATE = new BigDecimal("85.00");
    private static final BigDecimal DEFAULT_DISCOUNT_FEE = new BigDecimal("2.50");
    private static final int LOOKBACK_DAYS = 90;

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
                baseMetrics.gmv90d(),
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
        return factoredInvoiceRepository.save(factored);
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
        BigDecimal baseLimit = metrics.gmv90d().multiply(new BigDecimal("0.15"));
        BigDecimal velocityBoost = metrics.paymentVelocityScore()
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("0.10"));
        BigDecimal agingPenalty = metrics.avgInvoiceAgeDays()
                .divide(new BigDecimal("90"), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE)
                .multiply(new BigDecimal("0.20"));

        BigDecimal multiplier = BigDecimal.ONE.add(velocityBoost).subtract(agingPenalty).max(new BigDecimal("0.50"));
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

    public UnderwritingMetrics computeMetrics(UUID tenantId) {
        Instant cutoff = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<Invoice> invoices = invoiceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        BigDecimal gmv90d = invoices.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().isAfter(cutoff))
                .filter(i -> List.of("OPEN", "PARTIALLY_PAID", "PAID").contains(i.getStatus()))
                .map(Invoice::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Invoice> openInvoices = invoices.stream()
                .filter(i -> List.of("OPEN", "PARTIALLY_PAID").contains(i.getStatus()))
                .toList();

        double avgAgeDays = openInvoices.isEmpty() ? 0.0 : openInvoices.stream()
                .mapToDouble(i -> {
                    Instant anchor = i.getDueAt() != null ? i.getDueAt() : i.getCreatedAt();
                    return ChronoUnit.DAYS.between(anchor, Instant.now());
                })
                .average()
                .orElse(0.0);

        BigDecimal payments90d = paymentRepository.findByTenantId(tenantId).stream()
                .filter(p -> p.getSettledAt() != null && p.getSettledAt().isAfter(cutoff))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paymentVelocity = payments90d.divide(new BigDecimal(LOOKBACK_DAYS), 4, RoundingMode.HALF_UP);
        BigDecimal invoiceVelocity = gmv90d.divide(new BigDecimal(LOOKBACK_DAYS), 4, RoundingMode.HALF_UP);

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
                gmv90d.setScale(2, RoundingMode.HALF_UP),
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
            BigDecimal gmv90d,
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
