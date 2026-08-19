package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.integration.accounting.AccountingConnectionTest;
import com.invsys.integration.accounting.AccountingSyncAdapter;
import com.invsys.integration.accounting.LedgerAccount;
import com.invsys.integration.accounting.StandardLedgerAccounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountingChartOfAccountsServiceTest {

    private static final UUID TENANT = UUID.fromString("e0000000-0000-4000-8000-000000000099");

    private AccountingSyncAdapter quickBooks;
    private AccountingChartOfAccountsService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        quickBooks = mock(AccountingSyncAdapter.class);
        when(quickBooks.system()).thenReturn("QUICKBOOKS");
        when(quickBooks.listAccounts(TENANT)).thenReturn(StandardLedgerAccounts.sandboxCatalog());
        when(quickBooks.provisionStandardAccounts(TENANT)).thenReturn(StandardLedgerAccounts.requiredDefaults());
        when(quickBooks.testConnection(TENANT)).thenReturn(AccountingConnectionTest.of(true, true, "ok"));
        service = new AccountingChartOfAccountsService(List.of(quickBooks));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listsAndProvisionsNormalizedProvider() {
        List<LedgerAccount> accounts = service.listAccounts("qbo");
        assertThat(accounts).extracting(LedgerAccount::code).contains("12000", "50000", "40000", "22000");
        assertThat(service.autoProvision("QUICKBOOKS")).hasSize(4);
        assertThat(service.testConnection("quickbooks").ok()).isTrue();
    }

    @Test
    void rejectsUnknownProvider() {
        assertThatThrownBy(() -> service.listAccounts("SAGE"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("QUICKBOOKS");
    }
}
