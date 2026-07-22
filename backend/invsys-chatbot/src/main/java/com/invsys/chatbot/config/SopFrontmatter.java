package com.invsys.chatbot.config;

import java.util.List;

/** Parsed YAML frontmatter from a {@code docs/sops/*.md} manual. */
public record SopFrontmatter(
        String title,
        String slug,
        String sourcePath,
        List<String> audienceRoles,
        List<String> routeHints
) {
}
