package com.invsys.service;

import com.invsys.domain.PageKnowledgeConfig;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the closest weGrowStock Page Info record for a browser pathname
 * (exact match, settings {@code ?tab=}, then longest prefix).
 */
public final class PageKnowledgeRouteMatcher {

    private PageKnowledgeRouteMatcher() {
    }

    public static Optional<PageKnowledgeConfig> match(String rawRoute, List<PageKnowledgeConfig> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return Optional.empty();
        }
        NormalizedRoute route = normalize(rawRoute);
        if (route.path().isBlank()) {
            return Optional.empty();
        }

        Optional<PageKnowledgeConfig> exactWithQuery = findExact(catalog, route.fullKey());
        if (exactWithQuery.isPresent()) {
            return exactWithQuery;
        }
        if (route.hasQuery()) {
            Optional<PageKnowledgeConfig> exactPath = findExact(catalog, route.path());
            if (exactPath.isPresent()) {
                return exactPath;
            }
        }
        return longestPrefix(catalog, route.path());
    }

    public static NormalizedRoute normalize(String rawRoute) {
        String raw = rawRoute == null || rawRoute.isBlank() ? "/" : rawRoute.strip();
        int hash = raw.indexOf('#');
        if (hash >= 0) {
            raw = raw.substring(0, hash);
        }
        String path;
        String query = "";
        int q = raw.indexOf('?');
        if (q >= 0) {
            path = raw.substring(0, q);
            query = raw.substring(q);
        } else {
            path = raw;
        }
        path = path.replaceAll("/+$", "");
        if (path.isBlank()) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String tab = readTab(query);
        String fullKey = ("/settings".equals(path) && tab != null)
                ? "/settings?tab=" + tab
                : path;
        return new NormalizedRoute(path, fullKey, tab != null || !query.isBlank());
    }

    private static String readTab(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String stripped = query.startsWith("?") ? query.substring(1) : query;
        for (String part : stripped.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq);
            if ("tab".equalsIgnoreCase(key)) {
                String value = part.substring(eq + 1).strip();
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private static Optional<PageKnowledgeConfig> findExact(List<PageKnowledgeConfig> catalog, String key) {
        return catalog.stream()
                .filter(row -> key.equalsIgnoreCase(row.getRoutePattern()))
                .findFirst();
    }

    private static Optional<PageKnowledgeConfig> longestPrefix(List<PageKnowledgeConfig> catalog, String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return catalog.stream()
                .filter(row -> {
                    String pattern = row.getRoutePattern();
                    if (pattern == null || pattern.contains("?")) {
                        return false;
                    }
                    String candidate = pattern.replaceAll("/+$", "");
                    if (candidate.isBlank()) {
                        candidate = "/";
                    }
                    String c = candidate.toLowerCase(Locale.ROOT);
                    return lower.equals(c) || lower.startsWith(c + "/");
                })
                .max(Comparator.comparingInt(row -> row.getRoutePattern().length()));
    }

    public record NormalizedRoute(String path, String fullKey, boolean hasQuery) {
    }
}
