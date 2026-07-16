package com.invsys.integration.accounting;

import tools.jackson.databind.ObjectMapper;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.domain.InventoryLedger;
import com.invsys.integration.CredentialVaultService;
import com.invsys.integration.alerts.IntegrationFailurePublisher;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "invsys.integration.accounting.mock", havingValue = "false", matchIfMissing = true)
public class XeroAdapter implements AccountingSyncAdapter {

    private final IntegrationSyncLogRepository syncLogRepository;
    private final IntegrationCredentialRepository credentialRepository;
    private final CredentialVaultService credentialVaultService;
    private final InventoryLedgerRepository ledgerRepository;
    private final IntegrationFailurePublisher failurePublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public XeroAdapter(IntegrationSyncLogRepository syncLogRepository,
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
        return "XERO";
    }

    @Override
    public IntegrationSyncLog syncInvoice(UUID tenantId, UUID invoiceId) {
        return skipped(tenantId, "INVOICE", invoiceId);
    }

    @Override
    public IntegrationSyncLog syncPayment(UUID tenantId, UUID invoiceId) {
        return skipped(tenantId, "PAYMENT", invoiceId);
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

            var creds = credentialRepository.findByTenantIdAndSystem(tenantId, system());
            if (creds.isEmpty() || entry == null) {
                log.setStatus("SKIPPED");
                return syncLogRepository.save(log);
            }

            String accessToken = new String(credentialVaultService.decrypt(creds.get().getCiphertext()),
                    StandardCharsets.UTF_8);

            BigDecimal amount = entry.getUnitCost() != null
                    ? entry.getUnitCost().multiply(entry.getQuantityDelta().abs())
                    : BigDecimal.ZERO;

            Map<String, Object> journal = new LinkedHashMap<>();
            journal.put("Narration", "COGS " + entry.getMovementType());
            journal.put("Date", LocalDate.now().toString());
            journal.put("JournalLines", List.of(Map.of(
                    "LineAmount", amount,
                    "AccountCode", "500",
                    "Description", entry.getMovementType() + " " + entry.getVariantId()
            )));
            journal.put("Status", "DRAFT");

            String body = objectMapper.writeValueAsString(Map.of("ManualJournals", List.of(journal)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.xero.com/api.xro/2.0/ManualJournals"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            boolean ok = status >= 200 && status < 300;
            log.setStatus(ok ? "SYNCED" : "FAILED");
            if (!ok) {
                log.setLastError("HTTP " + status + ": " + truncate(response.body()));
                IntegrationSyncLog saved = syncLogRepository.save(log);
                if (status == 401 || status == 403 || status >= 500) {
                    failurePublisher.publish(tenantId, system(), "HTTP_" + status,
                            truncate(response.body()), ledgerEntryId);
                }
                return saved;
            }
            return syncLogRepository.save(log);
        } catch (Exception ex) {
            IntegrationSyncLog failed = skipped(tenantId, "LEDGER_ENTRY", ledgerEntryId);
            failed.setStatus("FAILED");
            failed.setLastError(truncate(ex.getMessage()));
            IntegrationSyncLog saved = syncLogRepository.save(failed);
            failurePublisher.publish(tenantId, system(), "SYNC_EXCEPTION",
                    truncate(ex.getMessage()), ledgerEntryId);
            return saved;
        } finally {
            TenantContext.clear();
        }
    }

    private IntegrationSyncLog skipped(UUID tenantId, String entityType, UUID entityId) {
        IntegrationSyncLog log = new IntegrationSyncLog();
        log.setTenantId(tenantId);
        log.setSystem(system());
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setStatus("SKIPPED");
        return syncLogRepository.save(log);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
