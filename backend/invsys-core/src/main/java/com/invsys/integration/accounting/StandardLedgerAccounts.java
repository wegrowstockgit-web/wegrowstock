package com.invsys.integration.accounting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default inventory / COGS / revenue / tax accounts provisioned when a tenant ledger is empty.
 */
public final class StandardLedgerAccounts {

    public static final LedgerAccount INVENTORY_ASSET = new LedgerAccount(
            "std-12000", "12000 - Inventory Asset", "Other Current Asset", "ASSET", "12000");
    public static final LedgerAccount COGS = new LedgerAccount(
            "std-50000", "50000 - Cost of Goods Sold", "Cost of Goods Sold", "EXPENSE", "50000");
    public static final LedgerAccount SALES_REVENUE = new LedgerAccount(
            "std-40000", "40000 - Sales Revenue", "Income", "REVENUE", "40000");
    public static final LedgerAccount SALES_TAX = new LedgerAccount(
            "std-22000", "22000 - Sales Tax Payable", "Other Current Liability", "LIABILITY", "22000");

    private StandardLedgerAccounts() {
    }

    public static List<LedgerAccount> requiredDefaults() {
        return List.of(INVENTORY_ASSET, COGS, SALES_REVENUE, SALES_TAX);
    }

    /**
     * Demo / disconnected catalog so the mapping wizard can still recommend accounts.
     */
    public static List<LedgerAccount> sandboxCatalog() {
        List<LedgerAccount> accounts = new ArrayList<>(requiredDefaults());
        accounts.add(new LedgerAccount("std-10000", "10000 - Business Bank", "Bank", "ASSET", "10000"));
        accounts.add(new LedgerAccount("std-61000", "61000 - Office Supplies", "Expense", "EXPENSE", "61000"));
        return List.copyOf(accounts);
    }

    public static List<LedgerAccount> missingDefaults(List<LedgerAccount> existing) {
        return requiredDefaults().stream()
                .filter(required -> findMatch(existing, required).isEmpty())
                .toList();
    }

    public static Optional<LedgerAccount> findMatch(List<LedgerAccount> existing, LedgerAccount required) {
        if (existing == null || existing.isEmpty()) {
            return Optional.empty();
        }
        return existing.stream()
                .filter(account -> matches(account, required))
                .findFirst();
    }

    public static boolean matches(LedgerAccount account, LedgerAccount required) {
        if (account == null || required == null) {
            return false;
        }
        String code = normalize(account.code());
        if (!code.isBlank() && code.equals(normalize(required.code()))) {
            return true;
        }
        String name = normalize(account.name());
        String requiredName = normalize(required.name());
        if (!name.isBlank() && (name.equals(requiredName) || name.contains(coreName(requiredName)))) {
            return true;
        }
        return keywordsFor(required).stream().anyMatch(name::contains);
    }

    public static Set<String> presentCodes(List<LedgerAccount> accounts) {
        if (accounts == null) {
            return Set.of();
        }
        return accounts.stream()
                .map(LedgerAccount::code)
                .map(StandardLedgerAccounts::normalize)
                .filter(code -> !code.isBlank())
                .collect(Collectors.toSet());
    }

    private static Set<String> keywordsFor(LedgerAccount required) {
        String code = normalize(required.code());
        if ("12000".equals(code)) {
            return Set.of("inventory asset", "inventory", "stock on hand");
        }
        if ("50000".equals(code)) {
            return Set.of("cost of goods sold", "cost of sales", "cogs");
        }
        if ("40000".equals(code)) {
            return Set.of("sales revenue", "sales income", "trading income");
        }
        if ("22000".equals(code)) {
            return Set.of("sales tax payable", "vat payable", "gst payable");
        }
        return Set.of();
    }

    private static String coreName(String normalizedFullName) {
        int dash = normalizedFullName.indexOf(" - ");
        return dash >= 0 ? normalizedFullName.substring(dash + 3) : normalizedFullName;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
