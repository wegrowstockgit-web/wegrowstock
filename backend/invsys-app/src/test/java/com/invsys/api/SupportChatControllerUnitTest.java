package com.invsys.api;

import com.invsys.support.SupportActionProposal;
import com.invsys.support.SupportStructuredReply;
import com.invsys.support.dto.ActionDraft;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportChatControllerUnitTest {

    @Test
    void toDoneMapIncludesHttpMethodAndProactiveInsight() {
        ActionDraft draft = new ActionDraft(
                "Release picking wave",
                "Releases tasks to the floor",
                "/api/v1/picking/waves/w1/release",
                "POST",
                Map.of("supportAction", "releaseWave", "waveId", "w1"));
        SupportStructuredReply reply = SupportStructuredReply.of(
                        "Clear the hold, then allocate.",
                        List.of(SupportActionProposal.navigate("Sales Orders", "/sales-orders")),
                        List.of("How do I resolve these holds?"))
                .withActionDraft(draft)
                .withProactiveInsight("💡 3 orders are currently stuck on Credit Hold. Tap to review.");

        Map<String, Object> done = SupportChatController.toDoneMap(reply);

        assertThat(done).containsEntry("ok", true);
        assertThat(done.get("replyMarkdown").toString()).contains("Clear the hold");
        assertThat(done.get("proactiveInsight").toString()).contains("Credit Hold");
        @SuppressWarnings("unchecked")
        Map<String, Object> actionDraft = (Map<String, Object>) done.get("actionDraft");
        assertThat(actionDraft).containsEntry("httpMethod", "POST");
        assertThat(actionDraft).containsEntry("targetEndpoint", "/api/v1/picking/waves/w1/release");
        assertThat(actionDraft).containsEntry("title", "Release picking wave");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chips = (List<Map<String, Object>>) done.get("actionChips");
        assertThat(chips).isNotEmpty();
        assertThat(chips.getFirst()).containsEntry("action", "NAVIGATE");
    }

    @Test
    void toActionMapIncludesOptionalTarget() {
        Map<String, Object> withTarget = SupportChatController.toActionMap(
                SupportActionProposal.chip("SPOTLIGHT", "Un-allocate", "[data-tour='btn-unallocate']"));
        assertThat(withTarget).containsEntry("target", "[data-tour='btn-unallocate']");
        assertThat(withTarget).containsEntry("type", "action_chip");

        Map<String, Object> button = SupportChatController.toActionMap(
                SupportActionProposal.button("generateCycleCount", "Count Aisle-4", Map.of("zoneId", "Aisle-4")));
        assertThat(button).containsEntry("action", "generateCycleCount");
        assertThat(button.get("params")).isInstanceOf(Map.class);
    }
}
