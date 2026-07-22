package com.invsys.chatbot.config;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight YAML frontmatter parser for SOP manuals ({@code ---} … {@code ---}).
 * Avoids a hard dependency on a YAML library for this small schema.
 */
public final class SopFrontmatterParser {

    private static final Pattern FRONTMATTER = Pattern.compile(
            "(?s)\\A---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?(.*)\\z");
    private static final Pattern QUOTED = Pattern.compile("^\"(.*)\"$");
    private static final Pattern STRING_ARRAY = Pattern.compile("\\[(.*?)]");

    private SopFrontmatterParser() {
    }

    public record ParsedDocument(SopFrontmatter frontmatter, String body) {
    }

    public static ParsedDocument parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("SOP markdown is empty");
        }
        Matcher matcher = FRONTMATTER.matcher(markdown.stripLeading());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("SOP markdown is missing YAML frontmatter (--- ... ---)");
        }
        String yaml = matcher.group(1);
        String body = matcher.group(2) == null ? "" : matcher.group(2).strip();
        String title = null;
        String slug = null;
        String sourcePath = null;
        List<String> roles = List.of();
        List<String> routes = List.of();
        for (String rawLine : yaml.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            switch (key) {
                case "title" -> title = unquote(value);
                case "slug" -> slug = unquote(value);
                case "sourcePath", "source_path" -> sourcePath = unquote(value);
                case "audienceRoles", "audience_roles" -> roles = parseStringArray(value);
                case "routeHints", "route_hints" -> routes = parseStringArray(value);
                default -> {
                    // ignore unknown keys
                }
            }
        }
        if (!StringUtils.hasText(title)) {
            title = "Untitled SOP";
        }
        if (!StringUtils.hasText(slug)) {
            slug = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        }
        return new ParsedDocument(new SopFrontmatter(title, slug, sourcePath, roles, routes), body);
    }

    private static String unquote(String value) {
        if (value == null) {
            return "";
        }
        Matcher m = QUOTED.matcher(value.strip());
        if (m.matches()) {
            return m.group(1).replace("\\\"", "\"");
        }
        return value.strip();
    }

    private static List<String> parseStringArray(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Matcher m = STRING_ARRAY.matcher(value.strip());
        if (!m.find()) {
            return List.of();
        }
        String inner = m.group(1).strip();
        if (inner.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : inner.split(",")) {
            String item = unquote(part.strip());
            if (StringUtils.hasText(item)) {
                out.add(item);
            }
        }
        return List.copyOf(out);
    }
}
