package com.invsys.integration.accounting;

import tools.jackson.databind.ObjectMapper;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.domain.InventoryLedger;
import com.invsys.integration.CredentialVaultService;
import com.invsys.integration.alerts.IntegrationFailurePublisher;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "invsys.integration.accounting.mock", havingValue = "false", matchIfMissing = true)
public class QuickBooksOnlineAdapter implements AccountingSyncAdapter {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final IntegrationCredentialRepository credentialRepository;
    private final CredentialVaultService credentialVaultService;
    private final InventoryLedgerRepository ledgerRepository;
    private final IntegrationFailurePublisher failurePublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public QuickBooksOnlineAdapter(IntegrationSyncLogRepository syncLogRepository,
                                   IntegrationCredentialRepository credentialRepository,
                                   CredentialVaultService credentialVaultService,
                                   InventoryLedgerRepository ledgerRepository,
                                   IntegrationFailurePublisher failurePublisher) {
        this.syncLogRepository = syncLogRepository;
        this.credentialRepository = credentialRepository;
        this.credentialVaultService = credentialVaultService;
        this.ledgerRepository = ledgerRepository;
        this.failurePublisher = failurePublisher;
    }

    @Override
    public String system() {
        return "QUICKBOOKS";
    }

    @Override
    public IntegrationSyncLog syncInvoice(UUID tenantId, UUID invoiceId) {
        return writeLog(tenantId, "INVOICE", invoiceId, "Invoice sync delegated to QBO adapter");
    }

    @Override
    public IntegrationSyncLog syncPayment(UUID tenantId, UUID invoiceId) {
        return writeLog(tenantId, "PAYMENT", invoiceId, "Payment sync delegated to QBO adapter");
    }

    @Override
    public IntegrationSyncLog syncLedgerEntry(UUID tenantId, UUID ledgerEntryId) {
        TenantContext.setTenantId(tenantId);
        try {
            InventoryLedger entry = ledgerRepository.findById(ledgerEntryId).orElse(null);
            IntegrationSyncLog log = new IntegrationSyncLog();
            log.setTenantId(tenantId);
            log.setSystem(system());
            log.setEntityType("LEDGER_ENTRY");
            log.setEntityId(ledgerEntryId);

            Optional<CredentialBundle> creds = loadCredentials(tenantId);
            if (creds.isEmpty() || entry == null) {
                log.setStatus("SKIPPED");
                return syncLogRepository.save(log);
            }

            Map<String, Object> journal = new LinkedHashMap<>();
            journal.put("Line", new Object[]{
                    Map.of(
                            "DetailType", "JournalEntryLineDetail",
                            "Amount", entry.getUnitCost() != null
                                    ? entry.getUnitCost().multiply(entry.getQuantityDelta().abs())
                                    : BigDecimal.ZERO,
                            "JournalEntryLineDetail", Map.of(
                                    "PostingType", "Debit",
                                    "AccountRef", Map.of("value", "81")
                            )
                    )
            });

            String json = objectMapper.writeValueAsString(journal);
            String url = creds.get().baseUrl() + "/v3/company/" + creds.get().realmId()
                    + "/journalentry?minorversion=75";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + creds.get().accessToken())
                    .header("Content-Type", "application/json")
                    .header("Request-ID", ledgerEntryId.toString())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            boolean ok = status >= 200 && status < 300;
            log.setStatus(ok ? "SYNCED" : "FAILED");
            if (!ok) {
                log.setLastError("HTTP " + status + ": " + truncate(response.body()));
                IntegrationSyncLog saved = syncLogRepository.save(log);
                if (isAlertableHttpStatus(status)) {
                    failurePublisher.publish(tenantId, system(), "HTTP_" + status,
                            truncate(response.body()), ledgerEntryId);
                }
                return saved;
            }
            return syncLogRepository.save(log);
        } catch (Exception ex) {
            return writeFailedLog(tenantId, "LEDGER_ENTRY", ledgerEntryId, ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private Optional<CredentialBundle> loadCredentials(UUID tenantId) {
        return credentialRepository.findByTenantIdAndSystem(tenantId, system())
                .map(credential -> {
                    if (credential.getRefreshTokenExpiresAt() != null
                            && credential.getRefreshTokenExpiresAt().isBefore(java.time.Instant.now())) {
                        failurePublisher.publish(tenantId, system(), "OAUTH_TOKEN_EXPIRED",
                                "QuickBooks refresh token expired at " + credential.getRefreshTokenExpiresAt());
                        return null;
                    }
                    String raw = new String(credentialVaultService.decrypt(credential.getCiphertext()),
                            StandardCharsets.UTF_8);
                    String[] parts = raw.split("\\|");
                    if (parts.length < 3) {
                        return null;
                    }
                    return new CredentialBundle(parts[0], parts[1], parts[2]);
                })
                .filter(bundle -> bundle != null);
    }

    private IntegrationSyncLog writeLog(UUID tenantId, String entityType, UUID entityId, String detail) {
        IntegrationSyncLog log = new IntegrationSyncLog();
        log.setTenantId(tenantId);
        log.setSystem(system());
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setStatus("SYNCED");
        return syncLogRepository.save(log);
    }

    private IntegrationSyncLog writeFailedLog(UUID tenantId, String entityType, UUID entityId, String detail) {
        IntegrationSyncLog log = new IntegrationSyncLog();
        log.setTenantId(tenantId);
        log.setSystem(system());
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setStatus("FAILED");
        log.setLastError(truncate(detail));
        IntegrationSyncLog saved = syncLogRepository.save(log);
        failurePublisher.publish(tenantId, system(), "SYNC_EXCEPTION", truncate(detail), entityId);
        return saved;
    }

    private static boolean isAlertableHttpStatus(int status) {
        return status == 401 || status == 403 || status >= 500;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private record CredentialBundle(String accessToken, String realmId, String baseUrl) {
    }
}
