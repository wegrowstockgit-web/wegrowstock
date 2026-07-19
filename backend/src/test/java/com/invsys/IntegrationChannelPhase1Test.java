package com.invsys;

import com.invsys.api.dto.IntegrationChannelUpsertRequest;
import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.IntegrationChannel;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.integration.channel.IntegrationChannelStatus;
import com.invsys.integration.channel.IntegrationChannelType;
import com.invsys.integration.channel.SyncDirection;
import com.invsys.integration.channel.SyncEntityType;
import com.invsys.integration.channel.SyncLogStatus;
import com.invsys.repository.IntegrationChannelRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.service.IntegrationChannelService;
import com.invsys.service.IntegrationSyncHistoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IntegrationChannelPhase1Test extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired IntegrationChannelService channelService;
    @Autowired IntegrationChannelRepository channelRepository;
    @Autowired IntegrationSyncHistoryService syncHistoryService;
    @Autowired IntegrationSyncLogRepository syncLogRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upsertEncryptsCredentials_andActiveLookupDecrypts() {
        String slug = "ch1-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Channel Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        TenantContext.setTenantId(owner.tenantId());

        channelService.upsert(new IntegrationChannelUpsertRequest(
                "SHOPIFY",
                "ACTIVE",
                Map.of(
                        "accessToken", "shpat-secret-token",
                        "webhookSecret", "whsec-abc"),
                Map.of("shopDomain", slug + ".myshopify.com")));

        IntegrationChannel stored = channelRepository
                .findActiveByTenantAndType(owner.tenantId(), IntegrationChannelType.SHOPIFY)
                .orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(IntegrationChannelStatus.ACTIVE);
        assertThat(stored.getEncryptedCredentials()).isNotNull();
        String cipherAsText = new String(stored.getEncryptedCredentials());
        assertThat(cipherAsText).doesNotContain("shpat-secret-token");
        assertThat(cipherAsText).doesNotContain("whsec-abc");
        assertThat(stored.getSettings()).containsEntry("shopDomain", slug + ".myshopify.com");

        Map<String, String> secrets = channelService.loadCredentials(IntegrationChannelType.SHOPIFY);
        assertThat(secrets).containsEntry("accessToken", "shpat-secret-token");
        assertThat(secrets).containsEntry("webhookSecret", "whsec-abc");
    }

    @Test
    void syncHistoryRecordsPhase1Fields() {
        String slug = "ch2-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Sync Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        TenantContext.setTenantId(owner.tenantId());

        IntegrationChannel channel = channelRepository.save(activeChannel(
                owner.tenantId(), IntegrationChannelType.EDI, Map.of("as2Id", "PARTNER-1")));

        IntegrationSyncLog log = syncHistoryService.record(
                channel,
                SyncDirection.INBOUND,
                SyncEntityType.ORDER,
                "PO-100",
                SyncLogStatus.SUCCESS,
                Map.of("lineCount", 3),
                null);

        assertThat(log.getChannelId()).isEqualTo(channel.getId());
        assertThat(log.getDirection()).isEqualTo(SyncDirection.INBOUND);
        assertThat(log.getEntityType()).isEqualTo("ORDER");
        assertThat(log.getExternalId()).isEqualTo("PO-100");
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getPayloadSummary()).containsEntry("lineCount", 3);
        assertThat(log.getProcessedAt()).isNotNull();
        assertThat(log.getEntityId()).isNull();

        assertThat(syncLogRepository.findByTenantIdAndChannelIdAndExternalId(
                owner.tenantId(), channel.getId(), "PO-100")).isPresent();
    }

    @Test
    void httpUpsertAndListChannels_hidesSecrets() throws Exception {
        String slug = "ch3-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Http Channel", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(put("/api/v1/integrations/hub/channels/AMAZON")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channelType":"AMAZON",
                                  "status":"ACTIVE",
                                  "credentials":{"refreshToken":"amz-refresh-secret"},
                                  "settings":{"marketplaceId":"ATVPDKIKX0DER"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channelType").value("AMAZON"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.credentialsConfigured").value(true))
                .andExpect(jsonPath("$.settings.marketplaceId").value("ATVPDKIKX0DER"))
                .andExpect(jsonPath("$.credentials").doesNotExist())
                .andExpect(jsonPath("$.encryptedCredentials").doesNotExist());

        mockMvc.perform(get("/api/v1/integrations/hub/channels")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channelType").value("AMAZON"))
                .andExpect(jsonPath("$[0].credentialsConfigured").value(true));

        String body = mockMvc.perform(get("/api/v1/integrations/hub/channels")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("amz-refresh-secret");
    }

    private IntegrationChannel activeChannel(UUID tenantId,
                                             IntegrationChannelType type,
                                             Map<String, Object> settings) {
        IntegrationChannel channel = new IntegrationChannel();
        channel.setTenantId(tenantId);
        channel.setChannelType(type);
        channel.setStatus(IntegrationChannelStatus.ACTIVE);
        channel.setSettings(settings);
        return channel;
    }
}
