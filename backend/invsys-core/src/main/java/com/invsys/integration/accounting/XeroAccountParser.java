package com.invsys.integration.accounting;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class XeroAccountParser {

    private XeroAccountParser() {
    }

    public static List<LedgerAccount> parseAccounts(ObjectMapper objectMapper, String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode accounts = root.path("Accounts");
        if (!accounts.isArray()) {
            return List.of();
        }
        List<LedgerAccount> result = new ArrayList<>();
        for (JsonNode node : accounts) {
            LedgerAccount parsed = toAccount(node);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return List.copyOf(result);
    }

    public static LedgerAccount parseCreated(ObjectMapper objectMapper, String body) {
        List<LedgerAccount> accounts = parseAccounts(objectMapper, body);
        return accounts.isEmpty() ? null : accounts.getFirst();
    }

    static LedgerAccount toAccount(JsonNode node) {
        String id = firstText(node, "AccountID", "AccountId");
        String name = text(node, "Name");
        if (id.isBlank() && name.isBlank()) {
            return null;
        }
        return new LedgerAccount(
                id.isBlank() ? name : id,
                name.isBlank() ? id : name,
                text(node, "Type"),
                firstText(node, "Class", "ReportingCodeName"),
                text(node, "Code"));
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }
}
