package com.invsys.support;

import com.invsys.support.dto.ActionDraft;

import java.util.List;

/**
 * Strongly typed copilot completion payload (SSE {@code done} event + action streams).
 */
public record SupportStructuredReply(
        String replyMarkdown,
        List<SupportActionProposal> actionChips,
        List<String> followUpQuestions,
        ActionDraft actionDraft,
        String proactiveInsight
) {
    public SupportStructuredReply {
        replyMarkdown = replyMarkdown == null ? "" : replyMarkdown;
        actionChips = actionChips == null ? List.of() : List.copyOf(actionChips);
        followUpQuestions = followUpQuestions == null ? List.of() : List.copyOf(followUpQuestions);
        proactiveInsight = proactiveInsight == null || proactiveInsight.isBlank() ? null : proactiveInsight.strip();
    }

    public static SupportStructuredReply of(
            String replyMarkdown,
            List<SupportActionProposal> actionChips,
            List<String> followUpQuestions
    ) {
        return new SupportStructuredReply(replyMarkdown, actionChips, followUpQuestions, null, null);
    }

    public SupportStructuredReply withActionDraft(ActionDraft draft) {
        return new SupportStructuredReply(replyMarkdown, actionChips, followUpQuestions, draft, proactiveInsight);
    }

    public SupportStructuredReply withProactiveInsight(String insight) {
        return new SupportStructuredReply(replyMarkdown, actionChips, followUpQuestions, actionDraft, insight);
    }
}
