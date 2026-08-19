package com.invsys.integration.accounting;

public record AccountingConnectionTest(
        boolean ok,
        boolean readOk,
        boolean writeOk,
        String message
) {
    public static AccountingConnectionTest of(boolean readOk, boolean writeOk, String message) {
        return new AccountingConnectionTest(readOk && writeOk, readOk, writeOk, message);
    }
}
