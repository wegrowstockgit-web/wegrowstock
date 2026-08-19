package com.invsys.integration.accounting;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class QuickBooksAccountParser {

    private QuickBooksAccountParser() {
    }

    public static List<LedgerAccount> parseQuery(ObjectMapper objectMapper, String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode accounts = root.path("QueryResponse").path("Account");
        if (!accounts.isArray()) {
            JsonNode single = root.path("Account");
            if (single.isObject()) {
                LedgerAccount parsed = toAccount(single);
                return parsed == null ? List.of() : List.of(parsed);
            }
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
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode account = objectMapper.readTree(body).path("Account");
        return account.isObject() ? toAccount(account) : null;
    }

    static LedgerAccount toAccount(JsonNode node) {
        String id = text(node, "Id");
        String name = text(node, "Name");
        if (id.isBlank() && name.isBlank()) {
            return null;
        }
        String type = text(node, "AccountType");
        String classification = text(node, "Classification");
        String code = text(node, "AcctNum");
        if (code.isBlank()) {
            code = text(node, "FullyQualifiedName");
        }
        return new LedgerAccount(
                id.isBlank() ? name : id,
                name.isBlank() ? id : name,
                type,
                classification,
                code);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }
}
