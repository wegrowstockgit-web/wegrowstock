package com.invsys.support.dto;

import com.invsys.support.SupportActionProposal;
import com.invsys.support.SupportStructuredReply;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Structured LLM / SSE completion contract for the Support Copilot.
 *
 * <p>{@code actionChips} drive generative UI: {@code NAVIGATE}, {@code SPOTLIGHT}, {@code START_TOUR}.
 * Disallowed chip actions are stripped in {@link #toStructuredReply()}.
 */
public record SupportChatResponse(
        String replyMarkdown,
        String proactiveInsight,
        List<ActionChip> actionChips,
        ActionDraft actionDraft,
        List<String> followUpQuestions
) {
    public SupportChatResponse {
        replyMarkdown = replyMarkdown == null ? "" : replyMarkdown;
        proactiveInsight = proactiveInsight == null || proactiveInsight.isBlank()
                ? null
                : proactiveInsight.strip();
        actionChips = actionChips == null ? List.of() : List.copyOf(actionChips);
        followUpQuestions = followUpQuestions == null ? List.of() : List.copyOf(followUpQuestions);
    }

    public SupportChatResponse(String replyMarkdown, List<ActionChip> actionChips, List<String> followUpQuestions) {
        this(replyMarkdown, null, actionChips, null, followUpQuestions);
    }

    public SupportChatResponse(
            String replyMarkdown,
            List<ActionChip> actionChips,
            List<String> followUpQuestions,
            ActionDraft actionDraft
    ) {
        this(replyMarkdown, null, actionChips, actionDraft, followUpQuestions);
    }

    /**
     * Generative UI chip. Allowed {@code action} values: NAVIGATE, SPOTLIGHT, START_TOUR.
     */
    public record ActionChip(String label, String action, String target) {
        public ActionChip {
            label = label == null ? "" : label;
            action = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
            target = target == null ? "" : target;
        }

        public boolean isAllowed() {
            return "NAVIGATE".equals(action) || "SPOTLIGHT".equals(action) || "START_TOUR".equals(action);
        }
    }

    public SupportStructuredReply toStructuredReply() {
        List<SupportActionProposal> chips = new ArrayList<>();
        for (ActionChip chip : actionChips) {
            if (chip.isAllowed()) {
                chips.add(SupportActionProposal.chip(chip.action(), chip.label(), chip.target()));
            }
        }
        return SupportStructuredReply.of(replyMarkdown, chips, followUpQuestions)
                .withActionDraft(actionDraft)
                .withProactiveInsight(proactiveInsight);
    }

    public static SupportChatResponse fromStructured(SupportStructuredReply reply) {
        if (reply == null) {
            return new SupportChatResponse("", null, List.of(), null, List.of());
        }
        List<ActionChip> chips = new ArrayList<>();
        for (SupportActionProposal proposal : reply.actionChips()) {
            if ("action_chip".equals(proposal.type())) {
                chips.add(new ActionChip(proposal.label(), proposal.action(), proposal.target()));
            }
        }
        return new SupportChatResponse(
                reply.replyMarkdown(),
                reply.proactiveInsight(),
                chips,
                reply.actionDraft(),
                reply.followUpQuestions());
    }
}
