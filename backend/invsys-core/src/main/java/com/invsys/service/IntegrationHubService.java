package com.invsys.service;

import com.invsys.domain.ChannelIntegration;
import com.invsys.domain.IntegrationChannel;
import com.invsys.integration.channel.IntegrationChannelStatus;
import com.invsys.integration.channel.IntegrationChannelType;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.repository.ChannelIntegrationRepository;
import com.invsys.repository.EdiTradingPartnerRepository;
import com.invsys.repository.IntegrationChannelRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates multi-tenant integration connection status for the Settings Integrations Hub.
 */
@Service
public class IntegrationHubService {

    private static final Duration TOKEN_EXPIRING_WINDOW = Duration.ofDays(7);

    private final IntegrationCredentialRepository credentialRepository;
    private final ChannelIntegrationRepository channelIntegrationRepository;
    private final EdiTradingPartnerRepository ediTradingPartnerRepository;
    private final IntegrationChannelRepository integrationChannelRepository;
    private final IntegrationSyncLogRepository syncLogRepository;

    public IntegrationHubService(IntegrationCredentialRepository credentialRepository,
                                 ChannelIntegrationRepository channelIntegrationRepository,
                                 EdiTradingPartnerRepository ediTradingPartnerRepository,
                                 IntegrationChannelRepository integrationChannelRepository,
                                 IntegrationSyncLogRepository syncLogRepository) {
        this.credentialRepository = credentialRepository;
        this.channelIntegrationRepository = channelIntegrationRepository;
        this.ediTradingPartnerRepository = ediTradingPartnerRepository;
        this.integrationChannelRepository = integrationChannelRepository;
        this.syncLogRepository = syncLogRepository;
    }

    public HubStatus hubStatus() {
        UUID tenantId = TenantContext.requireTenantId();
        Set<String> connectedSystems = credentialRepository.findByTenantId(tenantId).stream()
                .filter(c -> "CONNECTED".equalsIgnoreCase(c.getStatus()))
                .map(IntegrationCredential::getSystem)
                .map(AccountingOAuthService::catalogSystem)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        Set<String> connectedChannels = channelIntegrationRepository
                .findByTenantIdOrderByPlatformAsc(tenantId).stream()
                .filter(c -> !"DISCONNECTED".equalsIgnoreCase(c.getStatus()))
                .map(ChannelIntegration::getPlatform)
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Set<IntegrationChannelType> activeHubChannels = integrationChannelRepository
                .findByTenantIdAndStatus(tenantId, IntegrationChannelStatus.ACTIVE).stream()
                .map(IntegrationChannel::getChannelType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(IntegrationChannelType.class)));

        boolean as2Connected = activeHubChannels.contains(IntegrationChannelType.EDI)
                || !ediTradingPartnerRepository.findByTenantId(tenantId).isEmpty();

        List<HubCategory> categories = new ArrayList<>();
        categories.add(new HubCategory(
                "ECOMMERCE",
                "E-Commerce",
                List.of(
                        card(tenantId, "SHOPIFY", "Shopify",
                                activeHubChannels.contains(IntegrationChannelType.SHOPIFY)
                                        || connectedChannels.contains("SHOPIFY")
                                        || connectedSystems.contains("SHOPIFY")),
                        card(tenantId, "AMAZON", "Amazon Seller Central",
                                activeHubChannels.contains(IntegrationChannelType.AMAZON)
                                        || connectedChannels.contains("AMAZON")
                                        || connectedSystems.contains("AMAZON"))
                )));
        categories.add(new HubCategory(
                "ACCOUNTING",
                "Accounting",
                List.of(
                        card(tenantId, "NETSUITE", "NetSuite", connectedSystems.contains("NETSUITE")),
                        card(tenantId, "XERO", "Xero", connectedSystems.contains("XERO")),
                        card(tenantId, "QUICKBOOKS", "QuickBooks", connectedSystems.contains("QUICKBOOKS"))
                )));
        categories.add(new HubCategory(
                "EDI",
                "B2B / EDI",
                List.of(card(tenantId, "AS2", "AS2 Trading Partners", as2Connected))));

        return new HubStatus(categories);
    }

    private HubCard card(UUID tenantId, String id, String name, boolean connected) {
        Instant lastSyncAt = syncLogRepository
                .findFirstByTenantIdAndSystemOrderByCreatedAtDesc(tenantId, id)
                .map(log -> log.getProcessedAt() != null ? log.getProcessedAt() : log.getCreatedAt())
                .orElse(null);
        long errorCount = syncLogRepository.countByTenantIdAndSystemAndStatus(tenantId, id, "FAILED");
        boolean tokenExpiringSoon = credentialRepository.findByTenantIdAndSystem(tenantId, id)
                .or(() -> credentialRepository.findByTenantIdAndSystem(tenantId, "OAUTH_" + id))
                .map(IntegrationCredential::getRefreshTokenExpiresAt)
                .filter(expires -> expires.isBefore(Instant.now().plus(TOKEN_EXPIRING_WINDOW)))
                .isPresent();
        String status;
        if (!connected) {
            status = "DISCONNECTED";
        } else if (tokenExpiringSoon) {
            status = "ACTION_REQUIRED";
        } else {
            status = "LIVE";
        }
        return new HubCard(
                id,
                name,
                status,
                connected,
                lastSyncAt == null ? "" : lastSyncAt.toString(),
                errorCount,
                tokenExpiringSoon);
    }

    public record HubStatus(List<HubCategory> categories) {
    }

    public record HubCategory(String id, String label, List<HubCard> integrations) {
    }

    public record HubCard(
            String id,
            String name,
            String status,
            boolean connected,
            String lastSyncAt,
            long errorCount,
            boolean tokenExpiringSoon
    ) {
    }
}
