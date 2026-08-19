package com.invsys.service;

import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingOAuthServiceTest {

    private static final UUID TENANT = UUID.fromString("e0000000-0000-4000-8000-000000000088");

    private BootstrapJdbc bootstrapJdbc;
    private IntegrationCredentialRepository credentialRepository;
    private IntegrationSyncLogRepository syncLogRepository;
    private AccountingOAuthService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        bootstrapJdbc = mock(BootstrapJdbc.class);
        credentialRepository = mock(IntegrationCredentialRepository.class);
        syncLogRepository = mock(IntegrationSyncLogRepository.class);
        service = new AccountingOAuthService(
                bootstrapJdbc,
                credentialRepository,
                syncLogRepository,
                "http://localhost:3000/api/v1/public/oauth/callback",
                "qbo-client",
                "https://appcenter.intuit.com/connect/oauth2",
                "com.intuit.quickbooks.accounting",
                "xero-client",
                "https://login.xero.com/identity/connect/authorize",
                "offline_access accounting.settings");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void authUrlIncludesConsentParamsAndMintsState() {
        AccountingOAuthService.AuthUrl url = service.authUrl("QUICKBOOKS");
        assertThat(url.authorizationUrl()).contains("https://appcenter.intuit.com/connect/oauth2");
        assertThat(url.authorizationUrl()).contains("client_id=qbo-client");
        assertThat(url.authorizationUrl()).contains("response_type=code");
        assertThat(url.authorizationUrl()).contains("redirect_uri=");
        assertThat(url.state()).isNotBlank();
        verify(bootstrapJdbc).insertOauthCallbackState(eq(url.state()), eq(TENANT), eq("quickbooks"), any(), any());
    }

    @Test
    void statusReportsConnectedNameAndExpiringToken() {
        IntegrationCredential credential = new IntegrationCredential();
        credential.setSystem("QUICKBOOKS");
        credential.setStatus("CONNECTED");
        credential.setRefreshTokenExpiresAt(Instant.now().plusSeconds(60));
        when(credentialRepository.findByTenantIdAndSystem(TENANT, "QUICKBOOKS"))
                .thenReturn(Optional.of(credential));
        when(syncLogRepository.findFirstByTenantIdAndSystemOrderByCreatedAtDesc(TENANT, "QUICKBOOKS"))
                .thenReturn(Optional.empty());

        AccountingOAuthService.ConnectionStatus status = service.status("qbo");
        assertThat(status.connected()).isTrue();
        assertThat(status.accountName()).isEqualTo("QuickBooks Online");
        assertThat(status.tokenExpiringSoon()).isTrue();
        assertThat(AccountingOAuthService.catalogSystem("OAUTH_XERO")).isEqualTo("XERO");
    }
}
