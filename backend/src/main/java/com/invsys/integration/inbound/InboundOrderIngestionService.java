package com.invsys.integration.inbound;

import com.invsys.common.ApiException;
import com.invsys.domain.IntegrationChannel;
import com.invsys.domain.SalesOrder;
import com.invsys.integration.channel.IntegrationChannelType;
import com.invsys.integration.channel.SyncDirection;
import com.invsys.integration.channel.SyncEntityType;
import com.invsys.integration.channel.SyncLogStatus;
import com.invsys.service.IntegrationChannelService;
import com.invsys.service.IntegrationSyncHistoryService;
import com.invsys.service.SalesOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Routes inbound channel payloads through registered {@link ExternalOrderAdapter}s into fulfillment,
 * recording Phase-1 {@code integration_sync_logs} rows for each attempt.
 */
@Service
public class InboundOrderIngestionService {

    private final List<ExternalOrderAdapter> adapters;
    private final SalesOrderService salesOrderService;
    private final IntegrationChannelService channelService;
    private final IntegrationSyncHistoryService syncHistoryService;

    public InboundOrderIngestionService(List<ExternalOrderAdapter> adapters,
                                        SalesOrderService salesOrderService,
                                        IntegrationChannelService channelService,
                                        IntegrationSyncHistoryService syncHistoryService) {
        this.adapters = List.copyOf(adapters);
        this.salesOrderService = salesOrderService;
        this.channelService = channelService;
        this.syncHistoryService = syncHistoryService;
    }

    @Transactional
    public SalesOrder ingest(String channelSource, String rawPayload, Map<String, String> headers) {
        Map<String, String> normalizedHeaders = normalizeHeaders(headers);
        String systemKey = normalizeSystemKey(channelSource);
        IntegrationChannel channel = resolveHubChannel(systemKey).orElse(null);

        try {
            ExternalOrderAdapter adapter = resolve(channelSource);
            CanonicalInboundOrder canonical = adapter.translate(rawPayload, normalizedHeaders);
            SalesOrder order = salesOrderService.createFromCanonical(canonical);
            SyncLogStatus status = "NEEDS_REVIEW".equals(order.getStatus())
                    ? SyncLogStatus.WARNING
                    : SyncLogStatus.SUCCESS;
            syncHistoryService.record(
                    channel,
                    systemKey,
                    SyncDirection.INBOUND,
                    SyncEntityType.ORDER,
                    canonical.externalOrderRef(),
                    order.getId(),
                    status,
                    Map.of(
                            "orderId", order.getId().toString(),
                            "orderNumber", order.getNumber(),
                            "orderStatus", order.getStatus(),
                            "channel", canonical.channelSource().name(),
                            "lineCount", canonical.lines().size()),
                    null);
            return order;
        } catch (RuntimeException ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            syncHistoryService.recordIsolated(
                    channel,
                    systemKey,
                    SyncDirection.INBOUND,
                    SyncEntityType.ORDER,
                    null,
                    SyncLogStatus.FAILED,
                    Map.of("channelSource", systemKey != null ? systemKey : ""),
                    message);
            throw ex;
        }
    }

    private Optional<IntegrationChannel> resolveHubChannel(String systemKey) {
        if (systemKey == null || systemKey.isBlank()) {
            return Optional.empty();
        }
        try {
            return channelService.findActive(IntegrationChannelType.valueOf(systemKey));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String normalizeSystemKey(String channelSource) {
        if (channelSource == null || channelSource.isBlank()) {
            return null;
        }
        String key = channelSource.trim().toUpperCase(Locale.ROOT);
        if ("AS2".equals(key) || "X12".equals(key)) {
            return "EDI";
        }
        return key;
    }

    private ExternalOrderAdapter resolve(String channelSource) {
        if (channelSource == null || channelSource.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "channelSource is required");
        }
        return adapters.stream()
                .filter(a -> a.supports(channelSource))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CHANNEL",
                        "No adapter registered for channel: " + channelSource)
                        .withProperty("channelSource", channelSource));
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(k, v);
            }
        });
        return out;
    }

    /** Exposed for tests / diagnostics. */
    public List<String> registeredChannels() {
        return adapters.stream()
                .flatMap(a -> List.of("SHOPIFY", "EDI", "AMAZON", "AS2", "X12").stream()
                        .filter(a::supports))
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }
}
