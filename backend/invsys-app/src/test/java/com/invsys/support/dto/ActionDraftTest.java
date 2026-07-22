package com.invsys.support.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionDraftTest {

    @Test
    void defaultsHttpMethodToPostWhenOmitted() {
        ActionDraft draft = new ActionDraft(
                "Title",
                "Desc",
                "/api/v1/cycle-counts",
                Map.of("zoneId", "A"));

        assertThat(draft.httpMethod()).isEqualTo("POST");
        assertThat(draft.payload()).containsEntry("zoneId", "A");
    }

    @Test
    void normalizesHttpMethodCaseAndBlanks() {
        assertThat(new ActionDraft("t", "d", "/api/v1/x", "patch", Map.of()).httpMethod())
                .isEqualTo("PATCH");
        assertThat(new ActionDraft("t", "d", "/api/v1/x", "  PUT  ", Map.of()).httpMethod())
                .isEqualTo("PUT");
        assertThat(new ActionDraft("t", "d", "/api/v1/x", "", Map.of()).httpMethod())
                .isEqualTo("POST");
        assertThat(new ActionDraft("t", "d", "/api/v1/x", "TRACE", Map.of()).httpMethod())
                .isEqualTo("POST");
    }

    @Test
    void nullFieldsBecomeSafeDefaults() {
        ActionDraft draft = new ActionDraft(null, null, null, null, null);
        assertThat(draft.title()).isEmpty();
        assertThat(draft.description()).isEmpty();
        assertThat(draft.targetEndpoint()).isEmpty();
        assertThat(draft.httpMethod()).isEqualTo("POST");
        assertThat(draft.payload()).isEmpty();
    }
}
