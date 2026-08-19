package com.invsys.integration.accounting;

/**
 * Provider chart-of-accounts row used by the mapping wizard.
 */
public record LedgerAccount(
        String accountId,
        String name,
        String type,
        String classification,
        String code
) {
}
