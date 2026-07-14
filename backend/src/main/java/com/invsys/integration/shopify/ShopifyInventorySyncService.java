package com.invsys.integration.shopify;

import com.invsys.domain.ChannelIntegration;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.integration.CredentialVaultService;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.repository.ChannelIntegrationRepository;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShopifyInventorySyncService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyInventorySyncService.class);

    private final ChannelIntegrationRepository channelIntegrationRepository;
    private final IntegrationCredentialRepository credentialRepository;
    private final CredentialVaultService credentialVaultService;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ShopifyInventorySyncService(ChannelIntegrationRepository channelIntegrationRepository,
                                       IntegrationCredentialRepository credentialRepository,
                                       CredentialVaultService credentialVaultService,
                                       InventoryLevelRepository inventoryLevelRepository,
                                       IntegrationSyncLogRepository syncLogRepository) {
        this.channelIntegrationRepository = channelIntegrationRepository;
        this.credentialRepository = credentialRepository;
        this.credentialVaultService = credentialVaultService;
        this.inventoryLevelRepository = inventoryLevelRepository;
        this.syncLogRepository = syncLogRepository;
    }

    public IntegrationSyncLog pushQuantity(UUID tenantId, UUID variantId, Map<String, Object> payload) {
        TenantContext.setTenantId(tenantId);
        try {
            BigDecimal onHand = inventoryLevelRepository.findByTenantIdAndVariantId(tenantId, variantId).stream()
                    .map(level -> level.getOnHand())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Optional<ChannelIntegration> integration = channelIntegrationRepository
                    .findByTenantIdOrderByPlatformAsc(tenantId).stream()
                    .filter(ci -> "SHOPIFY".equalsIgnoreCase(ci.getPlatform()))
                    .filter(ci -> "ACTIVE".equals(ci.getStatus()))
                    .findFirst();

            IntegrationSyncLog syncLog = new IntegrationSyncLog();
            syncLog.setTenantId(tenantId);
            syncLog.setSystem("SHOPIFY");
            syncLog.setEntityType("STOCK_LEVEL");
            syncLog.setEntityId(variantId);

            if (integration.isEmpty() || integration.get().getCredentialId() == null) {
                syncLog.setStatus("SKIPPED");
                syncLog.setLastError("No Shopify integration configured");
                return syncLogRepository.save(syncLog);
            }

            String accessToken = resolveAccessToken(integration.get().getCredentialId());
            String shop = integration.get().getShopIdentifier();
            String inventoryItemId = payload.getOrDefault("shopifyInventoryItemId", variantId.toString()).toString();
            String locationId = payload.getOrDefault("shopifyLocationId", "gid://shopify/Location/1").toString();

            String mutation = """
                    mutation inventorySetQuantities($input: InventorySetQuantitiesInput!) {
                      inventorySetQuantities(input: $input) {
                        inventoryAdjustmentGroup {
                          reason
                          changes { name delta }
                        }
                        userErrors { field message }
                      }
                    }
                    """;

            String body = """
                    {
                      "query": %s,
                      "variables": {
                        "input": {
                          "reason": "correction",
                          "name": "available",
                          "ignoreCompareQuantity": true,
                          "quantities": [{
                            "inventoryItemId": "%s",
                            "locationId": "%s",
                            "quantity": %s
                          }]
                        }
                      }
                    }
                    """.formatted(jsonString(mutation), inventoryItemId, locationId, onHand.toPlainString());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + shop + "/admin/api/2024-10/graphql.json"))
                    .header("Content-Type", "application/json")
                    .header("X-Shopify-Access-Token", accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            syncLog.setStatus(ok ? "SYNCED" : "FAILED");
            if (!ok) {
                syncLog.setLastError("HTTP " + response.statusCode() + ": " + truncate(response.body()));
                log.warn("Shopify inventorySetQuantities failed tenant={} variant={} status={}",
                        tenantId, variantId, response.statusCode());
            }
            return syncLogRepository.save(syncLog);
        } catch (Exception ex) {
            IntegrationSyncLog failed = new IntegrationSyncLog();
            failed.setTenantId(tenantId);
            failed.setSystem("SHOPIFY");
            failed.setEntityType("STOCK_LEVEL");
            failed.setEntityId(variantId);
            failed.setStatus("FAILED");
            failed.setLastError(truncate(ex.getMessage()));
            return syncLogRepository.save(failed);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveAccessToken(UUID credentialId) {
        IntegrationCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow();
        return new String(credentialVaultService.decrypt(credential.getCiphertext()), StandardCharsets.UTF_8);
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
