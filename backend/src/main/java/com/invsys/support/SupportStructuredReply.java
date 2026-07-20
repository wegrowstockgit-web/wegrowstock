package com.invsys.support;

import java.util.List;

/**
 * Strongly typed copilot completion payload (SSE {@code done} event + action streams).
 */
public record SupportStructuredReply(
        String replyMarkdown,
        List<SupportActionProposal> actionChips,
        List<String> followUpQuestions
) {
    public SupportStructuredReply {
        replyMarkdown = replyMarkdown == null ? "" : replyMarkdown;
        actionChips = actionChips == null ? List.of() : List.copyOf(actionChips);
        followUpQuestions = followUpQuestions == null ? List.of() : List.copyOf(followUpQuestions);
    }

    public static SupportStructuredReply of(
            String replyMarkdown,
            List<SupportActionProposal> actionChips,
            List<String> followUpQuestions
    ) {
        return new SupportStructuredReply(replyMarkdown, actionChips, followUpQuestions);
    }
}
