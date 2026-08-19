package com.invsys.integration.accounting;

import com.invsys.core.integration.CredentialVaultService;
import com.invsys.integration.alerts.IntegrationFailurePublisher;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuickBooksOnlineAdapterTest {

    private static final UUID TENANT = UUID.fromString("e0000000-0000-4000-8000-000000000077");

    @Test
    void fetchesAccountsAndCreatesMissingStandards() {
        IntegrationCredentialRepository credentials = mock(IntegrationCredentialRepository.class);
        CredentialVaultService vault = mock(CredentialVaultService.class);
        AccountingHttpTransport transport = mock(AccountingHttpTransport.class);
        IntegrationCredential stored = new IntegrationCredential();
        stored.setCiphertext("cipher".getBytes(StandardCharsets.UTF_8));
        when(credentials.findByTenantIdAndSystem(TENANT, "QUICKBOOKS")).thenReturn(Optional.of(stored));
        when(vault.decrypt(any())).thenReturn("token|realm-1|https://sandbox.qbo.example".getBytes(StandardCharsets.UTF_8));
        when(transport.get(contains("/query"), any())).thenReturn(new AccountingHttpTransport.Response(
                200,
                """
                {"QueryResponse":{"Account":[
                  {"Id":"1","Name":"Bank","AccountType":"Bank","AcctNum":"10000"}
                ]}}
                """));
        when(transport.post(contains("/account"), any(), anyString())).thenAnswer(invocation -> {
            String body = invocation.getArgument(2);
            String code = body.contains("22000") ? "22000"
                    : body.contains("40000") ? "40000"
                    : body.contains("50000") ? "50000"
                    : "12000";
            return new AccountingHttpTransport.Response(200,
                    "{\"Account\":{\"Id\":\"" + code + "\",\"Name\":\"" + code
                            + "\",\"AccountType\":\"Other Current Asset\",\"AcctNum\":\"" + code + "\"}}");
        });

        QuickBooksOnlineAdapter adapter = new QuickBooksOnlineAdapter(
                mock(IntegrationSyncLogRepository.class),
                credentials,
                vault,
                mock(InventoryLedgerRepository.class),
                mock(IntegrationFailurePublisher.class),
                transport);

        List<LedgerAccount> listed = adapter.listAccounts(TENANT);
        assertThat(listed).extracting(LedgerAccount::code).containsExactly("10000");

        List<LedgerAccount> provisioned = adapter.provisionStandardAccounts(TENANT);
        assertThat(provisioned).extracting(LedgerAccount::code)
                .contains("10000", "12000", "50000", "40000", "22000");
        verify(transport).post(contains("/account"), any(), contains("12000"));
        assertThat(adapter.testConnection(TENANT).ok()).isTrue();
    }

    @Test
    void fallsBackToSandboxWhenDisconnected() {
        IntegrationCredentialRepository credentials = mock(IntegrationCredentialRepository.class);
        when(credentials.findByTenantIdAndSystem(eq(TENANT), eq("QUICKBOOKS"))).thenReturn(Optional.empty());
        QuickBooksOnlineAdapter adapter = new QuickBooksOnlineAdapter(
                mock(IntegrationSyncLogRepository.class),
                credentials,
                mock(CredentialVaultService.class),
                mock(InventoryLedgerRepository.class),
                mock(IntegrationFailurePublisher.class),
                mock(AccountingHttpTransport.class));
        assertThat(adapter.listAccounts(TENANT)).isEqualTo(StandardLedgerAccounts.sandboxCatalog());
    }
}
