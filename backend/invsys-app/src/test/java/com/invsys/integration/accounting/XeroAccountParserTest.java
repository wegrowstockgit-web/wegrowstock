package com.invsys.integration.accounting;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XeroAccountParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesAccountList() {
        String body = """
                {
                  "Accounts": [
                    {
                      "AccountID": "aaa-1",
                      "Name": "Sales",
                      "Type": "REVENUE",
                      "Class": "REVENUE",
                      "Code": "40000"
                    }
                  ]
                }
                """;
        List<LedgerAccount> accounts = XeroAccountParser.parseAccounts(objectMapper, body);
        assertThat(accounts).hasSize(1);
        assertThat(accounts.getFirst().accountId()).isEqualTo("aaa-1");
        assertThat(XeroAccountParser.parseCreated(objectMapper, body).code()).isEqualTo("40000");
        assertThat(XeroAccountParser.parseAccounts(objectMapper, null)).isEmpty();
    }
}
