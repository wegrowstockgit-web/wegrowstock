package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.common.exception.AccountingProviderException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.integration.accounting.AccountingConnectionTest;
import com.invsys.integration.accounting.AccountingSyncAdapter;
import com.invsys.integration.accounting.LedgerAccount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccountingChartOfAccountsService {

    private final Map<String, AccountingSyncAdapter> adapters;

    public AccountingChartOfAccountsService(List<AccountingSyncAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(
                        adapter -> adapter.system().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (left, right) -> left));
    }

    public List<LedgerAccount> listAccounts(String provider) {
        return adapter(provider).listAccounts(TenantContext.requireTenantId());
    }

    public List<LedgerAccount> autoProvision(String provider) {
        return adapter(provider).provisionStandardAccounts(TenantContext.requireTenantId());
    }

    public AccountingConnectionTest testConnection(String provider) {
        return adapter(provider).testConnection(TenantContext.requireTenantId());
    }

    private AccountingSyncAdapter adapter(String provider) {
        String system = normalizeProvider(provider);
        AccountingSyncAdapter adapter = adapters.get(system);
        if (adapter == null) {
            throw new AccountingProviderException(
                    "UNSUPPORTED_PROVIDER",
                    "Accounting provider is not configured: " + system);
        }
        return adapter;
    }

    public static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "provider is required");
        }
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "QBO", "QUICKBOOKS_ONLINE" -> "QUICKBOOKS";
            case "QUICKBOOKS", "XERO" -> normalized;
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_PROVIDER",
                    "Supported providers: QUICKBOOKS, XERO");
        };
    }
}