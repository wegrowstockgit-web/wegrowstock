package com.invsys.integration.accounting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardLedgerAccountsTest {

    @Test
    void missingDefaultsDetectsStandardAccountsByCodeOrName() {
        List<LedgerAccount> existing = List.of(
                new LedgerAccount("1", "Inventory", "Other Current Asset", "ASSET", "12000"),
                new LedgerAccount("2", "Office Supplies", "Expense", "EXPENSE", "61000"));

        List<LedgerAccount> missing = StandardLedgerAccounts.missingDefaults(existing);

        assertThat(missing).extracting(LedgerAccount::code)
                .containsExactly("50000", "40000", "22000");
        assertThat(StandardLedgerAccounts.findMatch(existing, StandardLedgerAccounts.INVENTORY_ASSET)).isPresent();
        assertThat(StandardLedgerAccounts.sandboxCatalog()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void matchesKeywordsWhenCodeMissing() {
        LedgerAccount cogs = new LedgerAccount("9", "Cost of Goods Sold", "Expense", "EXPENSE", "");
        assertThat(StandardLedgerAccounts.matches(cogs, StandardLedgerAccounts.COGS)).isTrue();
        assertThat(StandardLedgerAccounts.presentCodes(List.of(StandardLedgerAccounts.SALES_REVENUE)))
                .contains("40000");
    }
}
