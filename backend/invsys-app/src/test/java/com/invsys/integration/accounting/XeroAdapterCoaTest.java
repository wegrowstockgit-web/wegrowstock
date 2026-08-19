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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XeroAdapterCoaTest {

    private static final UUID TENANT = UUID.fromString("e0000000-0000-4000-8000-000000000066");

    @Test
    void fetchesAndProvisionsXeroAccounts() {
        IntegrationCredentialRepository credentials = mock(IntegrationCredentialRepository.class);
        CredentialVaultService vault = mock(CredentialVaultService.class);
        AccountingHttpTransport transport = mock(AccountingHttpTransport.class);
        IntegrationCredential stored = new IntegrationCredential();
        stored.setCiphertext("cipher".getBytes(StandardCharsets.UTF_8));
        when(credentials.findByTenantIdAndSystem(TENANT, "XERO")).thenReturn(Optional.of(stored));
        when(vault.decrypt(any())).thenReturn("xero-token|tenant-1".getBytes(StandardCharsets.UTF_8));
        when(transport.get(contains("/Accounts"), any())).thenReturn(new AccountingHttpTransport.Response(
                200, "{\"Accounts\":[{\"AccountID\":\"a1\",\"Name\":\"Bank\",\"Type\":\"BANK\",\"Code\":\"090\"}]}"));
        when(transport.post(contains("/Accounts"), any(), anyString())).thenAnswer(invocation -> {
            String body = invocation.getArgument(2);
            String code = body.contains("22000") ? "22000"
                    : body.contains("40000") ? "40000"
                    : body.contains("50000") ? "50000"
                    : "12000";
            return new AccountingHttpTransport.Response(200,
                    "{\"Accounts\":[{\"AccountID\":\"" + code + "\",\"Name\":\"" + code
                            + "\",\"Type\":\"REVENUE\",\"Code\":\"" + code + "\"}]}");
        });

        XeroAdapter adapter = new XeroAdapter(
                mock(IntegrationSyncLogRepository.class),
                credentials,
                vault,
                mock(InventoryLedgerRepository.class),
                mock(IntegrationFailurePublisher.class),
                transport);

        assertThat(adapter.listAccounts(TENANT)).extracting(LedgerAccount::code).containsExactly("090");
        List<LedgerAccount> provisioned = adapter.provisionStandardAccounts(TENANT);
        assertThat(provisioned).extracting(LedgerAccount::code).contains("090", "12000", "50000");
        assertThat(adapter.testConnection(TENANT).ok()).isTrue();
    }
}
