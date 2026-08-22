package com.invsys.core.service;

import com.invsys.domain.PageKnowledgeConfig;
import com.invsys.service.PageKnowledgeRouteMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageKnowledgeRouteMatcherTest {

    @Test
    void exactSettingsTabBeatsBareSettings() {
        var users = config("/settings?tab=users");
        var settings = config("/settings");
        var hit = PageKnowledgeRouteMatcher.match("/settings?tab=users", List.of(settings, users));
        assertThat(hit).contains(users);
    }

    @Test
    void nestedIdUsesLongestPrefix() {
        var po = config("/purchase-orders");
        var purchasing = config("/purchasing");
        var hit = PageKnowledgeRouteMatcher.match("/purchase-orders/abc-123", List.of(po, purchasing));
        assertThat(hit).contains(po);
    }

    @Test
    void unknownRouteIsEmpty() {
        assertThat(PageKnowledgeRouteMatcher.match("/nope", List.of(config("/dashboard")))).isEmpty();
        assertThat(PageKnowledgeRouteMatcher.match("/dashboard", List.of())).isEmpty();
    }

    @Test
    void normalizeKeepsSettingsTabKey() {
        var route = PageKnowledgeRouteMatcher.normalize("/settings/?tab=operations#hash");
        assertThat(route.path()).isEqualTo("/settings");
        assertThat(route.fullKey()).isEqualTo("/settings?tab=operations");
        assertThat(route.hasQuery()).isTrue();
    }

    private static PageKnowledgeConfig config(String pattern) {
        PageKnowledgeConfig row = new PageKnowledgeConfig();
        row.setRoutePattern(pattern);
        row.setTitle(pattern);
        row.setCategory("Core");
        row.setSummary("s");
        row.setRolePrivileges("r");
        return row;
    }
}
