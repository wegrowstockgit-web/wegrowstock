package com.invsys.integration.inbound;

import com.invsys.domain.SalesOrder;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Unified authenticated edge for inbound commerce / EDI orders.
 */
@RestController
@RequestMapping("/api/v1/integrations")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class InboundIntegrationController {

    private final InboundOrderIngestionService ingestionService;

    public InboundIntegrationController(InboundOrderIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(
            value = "/inbound/{channelSource}",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE})
    public InboundOrderAcceptedResponse ingest(
            @PathVariable String channelSource,
            @RequestBody String rawPayload,
            @RequestHeader Map<String, String> headers) {
        SalesOrder order = ingestionService.ingest(channelSource, rawPayload, headers);
        return new InboundOrderAcceptedResponse(
                order.getId(),
                order.getNumber(),
                order.getStatus(),
                order.getChannel(),
                order.getCustomerPoNumber(),
                order.getCreatedAt());
    }

    public record InboundOrderAcceptedResponse(
            UUID id,
            String number,
            String status,
            String channel,
            String externalOrderRef,
            Instant createdAt
    ) {
    }
}
