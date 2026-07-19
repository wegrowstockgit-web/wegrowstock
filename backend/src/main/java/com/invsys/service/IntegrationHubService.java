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
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;

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

    private final IntegrationCredentialRepository credentialRepository;
    private final ChannelIntegrationRepository channelIntegrationRepository;
    private final EdiTradingPartnerRepository ediTradingPartnerRepository;
    private final IntegrationChannelRepository integrationChannelRepository;

    public IntegrationHubService(IntegrationCredentialRepository credentialRepository,
                                 ChannelIntegrationRepository channelIntegrationRepository,
                                 EdiTradingPartnerRepository ediTradingPartnerRepository,
                                 IntegrationChannelRepository integrationChannelRepository) {
        this.credentialRepository = credentialRepository;
        this.channelIntegrationRepository = channelIntegrationRepository;
        this.ediTradingPartnerRepository = ediTradingPartnerRepository;
        this.integrationChannelRepository = integrationChannelRepository;
    }

    public HubStatus hubStatus() {
        UUID tenantId = TenantContext.requireTenantId();
        Set<String> connectedSystems = credentialRepository.findByTenantId(tenantId).stream()
                .filter(c -> "CONNECTED".equalsIgnoreCase(c.getStatus()))
                .map(IntegrationCredential::getSystem)
                .map(s -> s.toUpperCase(Locale.ROOT))
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
                        card("SHOPIFY", "Shopify",
                                activeHubChannels.contains(IntegrationChannelType.SHOPIFY)
                                        || connectedChannels.contains("SHOPIFY")
                                        || connectedSystems.contains("SHOPIFY")),
                        card("AMAZON", "Amazon Seller Central",
                                activeHubChannels.contains(IntegrationChannelType.AMAZON)
                                        || connectedChannels.contains("AMAZON")
                                        || connectedSystems.contains("AMAZON"))
                )));
        categories.add(new HubCategory(
                "ACCOUNTING",
                "Accounting",
                List.of(
                        card("NETSUITE", "NetSuite", connectedSystems.contains("NETSUITE")),
                        card("XERO", "Xero", connectedSystems.contains("XERO")),
                        card("QUICKBOOKS", "QuickBooks", connectedSystems.contains("QUICKBOOKS"))
                )));
        categories.add(new HubCategory(
                "EDI",
                "B2B / EDI",
                List.of(card("AS2", "AS2 Trading Partners", as2Connected))));

        return new HubStatus(categories);
    }

    private static HubCard card(String id, String name, boolean connected) {
        return new HubCard(id, name, connected ? "CONNECTED" : "DISCONNECTED", connected);
    }

    public record HubStatus(List<HubCategory> categories) {
    }

    public record HubCategory(String id, String label, List<HubCard> integrations) {
    }

    public record HubCard(String id, String name, String status, boolean connected) {
    }
}
