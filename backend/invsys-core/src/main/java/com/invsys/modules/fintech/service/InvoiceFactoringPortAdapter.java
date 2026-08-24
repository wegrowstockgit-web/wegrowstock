package com.invsys.modules.fintech.service;

import com.invsys.modules.fintech.domain.FactoredInvoice;
import com.invsys.modules.fintech.repository.FactoredInvoiceRepository;
import com.invsys.modules.sales.api.InvoiceFactoringPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class InvoiceFactoringPortAdapter implements InvoiceFactoringPort {

    private final FactoredInvoiceRepository factoredInvoiceRepository;
    private final FintechUnderwritingService fintechUnderwritingService;

    InvoiceFactoringPortAdapter(FactoredInvoiceRepository factoredInvoiceRepository,
                                FintechUnderwritingService fintechUnderwritingService) {
        this.factoredInvoiceRepository = factoredInvoiceRepository;
        this.fintechUnderwritingService = fintechUnderwritingService;
    }

    @Override
    public Optional<String> fundingStatus(UUID tenantId, UUID invoiceId) {
        return factoredInvoiceRepository.findByTenantIdAndInvoiceId(tenantId, invoiceId)
                .map(FactoredInvoice::getFundingStatus);
    }

    @Override
    public FactoringResult requestFactoring(UUID invoiceId) {
        FactoredInvoice factored = fintechUnderwritingService.requestFactoring(invoiceId);
        return new FactoringResult(
                factored.getId(),
                factored.getInvoiceId(),
                factored.getAdvanceRate(),
                factored.getDiscountFeePercent(),
                factored.getFundingStatus(),
                factored.getEscrowPayoutRef());
    }
}
