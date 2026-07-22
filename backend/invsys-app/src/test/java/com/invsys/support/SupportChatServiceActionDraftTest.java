package com.invsys.support;

import com.invsys.support.dto.ActionDraft;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportChatServiceActionDraftTest {

    @Test
    void suggestDraftFromGenerateCycleCountActionButton() {
        ActionDraft draft = SupportChatService.suggestDraft(List.of(
                SupportActionProposal.button(
                        "generateCycleCount",
                        "Generate cycle count for Aisle-4",
                        Map.of("zoneId", "Aisle-4"))));

        assertThat(draft).isNotNull();
        assertThat(draft.title()).contains("Aisle-4");
        assertThat(draft.targetEndpoint()).isEqualTo("/api/v1/cycle-counts");
        assertThat(draft.httpMethod()).isEqualTo("POST");
        assertThat(draft.payload()).containsEntry("supportAction", "generateCycleCount");
        assertThat(draft.payload()).containsEntry("zoneId", "Aisle-4");
    }

    @Test
    void suggestDraftFromReleaseWave() {
        ActionDraft draft = SupportChatService.suggestDraft(List.of(
                SupportActionProposal.button(
                        "releaseWave",
                        "Release wave",
                        Map.of("waveId", "wave-1"))));

        assertThat(draft).isNotNull();
        assertThat(draft.title()).containsIgnoringCase("Release");
        assertThat(draft.targetEndpoint()).contains("/release");
        assertThat(draft.payload()).containsEntry("supportAction", "releaseWave");
    }

    @Test
    void suggestDraftReturnsNullForNavigateOnly() {
        assertThat(SupportChatService.suggestDraft(List.of(
                SupportActionProposal.navigate("Sales Orders", "/sales-orders")))).isNull();
        assertThat(SupportChatService.suggestDraft(null)).isNull();
        assertThat(SupportChatService.suggestDraft(List.of())).isNull();
    }

    @Test
    void suggestDraftFromQuestionBuildsUnallocateDraft() {
        ActionDraft draft = SupportChatService.suggestDraftFromQuestion(
                "Please unallocate stock on this order",
                Map.of("selectedEntityId", "SO-2026-00030"));

        assertThat(draft).isNotNull();
        assertThat(draft.title()).containsIgnoringCase("Un-allocate");
        assertThat(draft.targetEndpoint()).contains("/sales-orders/SO-2026-00030/allocate");
        assertThat(draft.payload()).containsEntry("intent", "unallocate");
        assertThat(draft.description()).contains("SO-2026-00030");
    }
}
