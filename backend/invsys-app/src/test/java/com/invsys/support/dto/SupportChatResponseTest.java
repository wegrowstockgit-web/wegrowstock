package com.invsys.support.dto;

import com.invsys.support.SupportActionProposal;
import com.invsys.support.SupportStructuredReply;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupportChatResponseTest {

    @Test
    void toStructuredReplyKeepsOnlyAllowedChipActions() {
        SupportChatResponse response = new SupportChatResponse(
                "Open Sales Orders and allocate.",
                List.of(
                        new SupportChatResponse.ActionChip("Go to Sales Orders", "NAVIGATE", "/sales-orders"),
                        new SupportChatResponse.ActionChip("Bad", "DELETE_TABLE", "inventory_ledger"),
                        new SupportChatResponse.ActionChip("Spotlight Allocate", "spotlight", "[data-testid=allocate]")),
                List.of("Why is it backordered?"));

        SupportStructuredReply structured = response.toStructuredReply();
        assertThat(structured.replyMarkdown()).contains("allocate");
        assertThat(structured.actionChips()).hasSize(2);
        assertThat(structured.actionChips())
                .extracting(SupportActionProposal::action)
                .containsExactlyInAnyOrder("NAVIGATE", "SPOTLIGHT");
        assertThat(structured.followUpQuestions()).containsExactly("Why is it backordered?");
    }

    @Test
    void fromStructuredMapsActionChips() {
        SupportStructuredReply reply = SupportStructuredReply.of(
                "Done",
                List.of(SupportActionProposal.navigate("Sales Orders", "/sales-orders")),
                List.of("Next?"));
        SupportChatResponse response = SupportChatResponse.fromStructured(reply);
        assertThat(response.actionChips()).hasSize(1);
        assertThat(response.actionChips().getFirst().action()).isEqualTo("NAVIGATE");
        assertThat(response.actionChips().getFirst().target()).isEqualTo("/sales-orders");
    }

    @Test
    void preservesOptionalActionDraftThroughStructuredRoundTrip() {
        ActionDraft draft = new ActionDraft(
                "Generate cycle count for Aisle-4",
                "Creates a worksheet",
                "/api/v1/cycle-counts",
                "POST",
                java.util.Map.of("supportAction", "generateCycleCount", "zoneId", "Aisle-4"));
        SupportChatResponse response = new SupportChatResponse(
                "I can start that count.",
                "💡 3 orders are currently stuck on Credit Hold. Tap to review.",
                List.of(),
                draft,
                List.of());

        SupportStructuredReply structured = response.toStructuredReply();
        assertThat(structured.actionDraft()).isEqualTo(draft);
        assertThat(structured.proactiveInsight()).contains("Credit Hold");

        SupportChatResponse roundTrip = SupportChatResponse.fromStructured(structured);
        assertThat(roundTrip.actionDraft()).isNotNull();
        assertThat(roundTrip.actionDraft().title()).contains("Aisle-4");
        assertThat(roundTrip.actionDraft().httpMethod()).isEqualTo("POST");
        assertThat(roundTrip.actionDraft().payload()).containsEntry("zoneId", "Aisle-4");
        assertThat(roundTrip.proactiveInsight()).contains("Credit Hold");
    }
}
