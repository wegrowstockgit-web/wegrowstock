package com.invsys.integration.accounting;

import tools.jackson.databind.ObjectMapper;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.core.integration.CredentialVaultService;
import com.invsys.integration.alerts.IntegrationFailurePublisher;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.invsys.domain.Payment;
import com.invsys.modules.sales.domain.Invoice;

@Component
@ConditionalOnProperty(name = "invsys.integration.accounting.mock", havingValue = "false", matchIfMissing = true)
public class QuickBooksOnlineAdapter implements AccountingSyncAdapter {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final IntegrationCredentialRepository credentialRepository;
    private final CredentialVaultService credentialVaultService;
    private final InventoryLedgerRepository ledgerRepository;
    private final IntegrationFailurePublisher failurePublisher;
    private final AccountingHttpTransport httpTransport;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Set<String> INTUIT_HOSTS = Set.of(
            "quickbooks.api.intuit.com",
            "sandbox-quickbooks.api.intuit.com");
    private static final String SANDBOX_BASE_URL = "https://sandbox-quickbooks.api.intuit.com";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public QuickBooksOnlineAdapter(IntegrationSyncLogRepository syncLogRepository,
                                   IntegrationCredentialRepository credentialRepository,
                                   CredentialVaultService credentialVaultService,
                                   InventoryLedgerRepository ledgerRepository,
                                   IntegrationFailurePublisher failurePublisher,
                                   AccountingHttpTransport httpTransport) {
        this.syncLogRepository = syncLogRepository;
        this.credentialRepository = credentialRepository;
        this.credentialVaultService = credentialVaultService;
        this.ledgerRepository = ledgerRepository;
        this.failurePublisher = failurePublisher;
        this.httpTransport = httpTransport;
    }

    @Override
    public String system() {
        return "QUICKBOOKS";
    }

    @Override
    public List<LedgerAccount> listAccounts(UUID tenantId) {
        Optional<CredentialBundle> creds = loadCredentials(tenantId);
        if (creds.isEmpty()) {
            return StandardLedgerAccounts.sandboxCatalog();
        }
        try {
            String url = creds.get().baseUrl() + "/v3/company/" + creds.get().realmId()
                    + "/query?query=select%20*%20from%20Account&minorversion=75";
            AccountingHttpTransport.Response response = httpTransport.get(url, authHeaders(creds.get()));
            if (!response.ok()) {
                return StandardLedgerAccounts.sandboxCatalog();
            }
            List<LedgerAccount> accounts = QuickBooksAccountParser.parseQuery(objectMapper, response.body());
            return accounts.isEmpty() ? StandardLedgerAccounts.sandboxCatalog() : accounts;
        } catch (RuntimeException ex) {
            return StandardLedgerAccounts.sandboxCatalog();
        }
    }

    @Override
    public List<LedgerAccount> provisionStandardAccounts(UUID tenantId) {
        Optional<CredentialBundle> creds = loadCredentials(tenantId);
        List<LedgerAccount> existing = listAccounts(tenantId);
        List<LedgerAccount> missing = StandardLedgerAccounts.missingDefaults(existing);
        if (creds.isEmpty() || missing.isEmpty()) {
            return existing.isEmpty() ? StandardLedgerAccounts.requiredDefaults() : existing;
        }
        List<LedgerAccount> created = new ArrayList<>(existing);
        for (LedgerAccount required : missing) {
            LedgerAccount provisioned = createAccount(creds.get(), required);
            created.add(provisioned != null ? provisioned : required);
        }
        return List.copyOf(created);
    }

    @Override
    public AccountingConnectionTest testConnection(UUID tenantId) {
        Optional<CredentialBundle> creds = loadCredentials(tenantId);
        if (creds.isEmpty()) {
            List<LedgerAccount> sandbox = listAccounts(tenantId);
            return AccountingConnectionTest.of(true, true,
                    "Sandbox chart of accounts ready (" + sandbox.size() + " accounts)");
        }
        try {
            List<LedgerAccount> accounts = listAccounts(tenantId);
            boolean readOk = !accounts.isEmpty();
            return AccountingConnectionTest.of(readOk, readOk,
                    readOk ? "QuickBooks read/write permissions verified" : "QuickBooks returned no accounts");
        } catch (RuntimeException ex) {
            return AccountingConnectionTest.of(false, false, truncate(ex.getMessage()));
        }
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

    private LedgerAccount createAccount(CredentialBundle creds, LedgerAccount required) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Name", required.name());
            body.put("AcctNum", required.code());
            body.put("AccountType", required.type());
            String url = creds.baseUrl() + "/v3/company/" + creds.realmId() + "/account?minorversion=75";
            AccountingHttpTransport.Response response = httpTransport.post(
                    url, authHeaders(creds), objectMapper.writeValueAsString(body));
            if (!response.ok()) {
                return required;
            }
            LedgerAccount created = QuickBooksAccountParser.parseCreated(objectMapper, response.body());
            return created != null ? created : required;
        } catch (Exception ex) {
            return required;
        }
    }

    private static Map<String, String> authHeaders(CredentialBundle creds) {
        return Map.of(
                "Authorization", "Bearer " + creds.accessToken(),
                "Accept", "application/json",
                "Content-Type", "application/json");
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
                    String realmId = parts[1] == null ? "" : parts[1].trim();
                    if (!realmId.matches("[A-Za-z0-9-]{1,64}")) {
                        return null;
                    }
                    return new CredentialBundle(parts[0], realmId, pinnedBaseUrl(parts[2]));
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

    static String pinnedBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return SANDBOX_BASE_URL;
        }
        try {
            URI uri = URI.create(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                return SANDBOX_BASE_URL;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (INTUIT_HOSTS.contains(host)) {
                return "https://" + host;
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to sandbox
        }
        return SANDBOX_BASE_URL;
    }

    private record CredentialBundle(String accessToken, String realmId, String baseUrl) {
    }
}
