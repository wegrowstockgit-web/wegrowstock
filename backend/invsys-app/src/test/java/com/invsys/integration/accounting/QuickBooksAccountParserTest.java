package com.invsys.integration.accounting;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuickBooksAccountParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesQueryResponseAndCreatedAccount() {
        String query = """
                {
                  "QueryResponse": {
                    "Account": [
                      {
                        "Id": "81",
                        "Name": "Inventory Asset",
                        "AccountType": "Other Current Asset",
                        "Classification": "Asset",
                        "AcctNum": "12000"
                      }
                    ]
                  }
                }
                """;
        List<LedgerAccount> accounts = QuickBooksAccountParser.parseQuery(objectMapper, query);
        assertThat(accounts).hasSize(1);
        assertThat(accounts.getFirst().accountId()).isEqualTo("81");
        assertThat(accounts.getFirst().code()).isEqualTo("12000");

        LedgerAccount created = QuickBooksAccountParser.parseCreated(objectMapper, """
                {"Account":{"Id":"99","Name":"Sales Revenue","AccountType":"Income","AcctNum":"40000"}}
                """);
        assertThat(created).isNotNull();
        assertThat(created.accountId()).isEqualTo("99");
        assertThat(QuickBooksAccountParser.parseQuery(objectMapper, "")).isEmpty();
    }
}
