package com.invsys.api;

import com.invsys.domain.EdiDocumentLog;
import com.invsys.service.EdiTranslationEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/edi")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class EdiController {

    private final EdiTranslationEngine ediTranslationEngine;

    public EdiController(EdiTranslationEngine ediTranslationEngine) {
        this.ediTranslationEngine = ediTranslationEngine;
    }

    @GetMapping("/documents")
    public List<EdiDocumentResponse> listDocuments() {
        return ediTranslationEngine.listDocuments().stream().map(this::toResponse).toList();
    }

    @PostMapping("/inbound/850")
    public InboundOrderResponse parseInbound850(@Valid @RequestBody Inbound850Request request) {
        EdiTranslationEngine.InboundOrder order = ediTranslationEngine.parseInbound850(
                request.tradingPartnerId(), request.payload());
        return new InboundOrderResponse(
                order.partnerId(),
                order.customerId(),
                order.poNumber(),
                order.lines().stream()
                        .map(l -> new InboundLineResponse(l.sku(), l.quantity()))
                        .toList()
        );
    }

    private EdiDocumentResponse toResponse(EdiDocumentLog log) {
        return new EdiDocumentResponse(
                log.getId(),
                log.getTradingPartnerId(),
                log.getDirection(),
                log.getDocumentType(),
                log.getStatus(),
                log.getCreatedAt()
        );
    }

    public record Inbound850Request(@NotNull UUID tradingPartnerId, @NotBlank String payload) {
    }

    public record InboundOrderResponse(
            UUID partnerId,
            UUID customerId,
            String poNumber,
            List<InboundLineResponse> lines
    ) {
    }

    public record InboundLineResponse(String sku, java.math.BigDecimal quantity) {
    }

    public record EdiDocumentResponse(
            UUID id,
            UUID tradingPartnerId,
            String direction,
            String documentType,
            String status,
            Instant createdAt
    ) {
    }
}
