package com.invsys.api;

import com.invsys.support.SupportActionProposal;
import com.invsys.support.SupportChatService;
import com.invsys.support.SupportStructuredReply;
import com.invsys.support.dto.ActionDraft;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Streaming support chat — GraphRAG + agentic action proposals; confirm execute endpoint.
 */
@RestController
@RequestMapping("/api/v1/support")
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class SupportChatController {

    private final SupportChatService supportChatService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SupportChatController(SupportChatService supportChatService) {
        this.supportChatService = supportChatService;
    }

    public record ChatRequest(
            String message,
            Map<String, Object> pageContext,
            Map<String, Object> routeContext,
            Map<String, Object> pageState,
            /** Optional top-level temporal UI breadcrumbs (also accepted inside pageState). */
            List<Map<String, Object>> recentBreadcrumbs,
            List<String> userRoles,
            String imageBase64,
            /** Alias accepted by multimodal clients (same bytes as {@code imageBase64}). */
            String base64Image,
            String imageMimeType
    ) {
        public ChatRequest {
            if (message == null) {
                message = "";
            }
            if (pageContext == null) {
                pageContext = Map.of();
            }
            if (routeContext == null) {
                routeContext = Map.of();
            }
            if (pageState == null) {
                pageState = Map.of();
            }
            if (recentBreadcrumbs == null) {
                recentBreadcrumbs = List.of();
            }
            if (userRoles == null) {
                userRoles = List.of();
            }
            if ((imageBase64 == null || imageBase64.isBlank())
                    && base64Image != null
                    && !base64Image.isBlank()) {
                imageBase64 = base64Image;
            }
        }
    }

    public record ExecuteActionRequest(
            @NotBlank String action,
            Map<String, String> params
    ) {
    }

    public record ExecuteDraftRequest(ActionDraft actionDraft) {
    }

    @GetMapping("/insights")
    public Map<String, Object> insights(
            @RequestParam(value = "route", required = false) String route,
            @RequestHeader(value = "X-Current-Route", required = false) String routeHeader
    ) {
        String resolved = route != null && !route.isBlank() ? route : (routeHeader == null ? "" : routeHeader);
        String insight = supportChatService.detectInsight(resolved);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("proactiveInsight", insight);
        return out;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestBody ChatRequest request,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @RequestHeader(value = "X-Current-Route", required = false) String routeHeader
    ) {
        SseEmitter emitter = new SseEmitter(120_000L);
        List<String> roles = SupportChatService.parseRolesHeader(rolesHeader);
        if (roles.isEmpty() && request.userRoles() != null && !request.userRoles().isEmpty()) {
            roles = SupportChatService.normalizeRoles(request.userRoles());
        }
        if (roles.isEmpty()) {
            roles = rolesFromSecurityContext();
        }
        String route = resolveRoute(routeHeader, request.routeContext(), request.pageState());
        List<String> roleSnapshot = roles;
        Map<String, Object> mergedPageState = mergePageState(request.pageState(), roleSnapshot, route);
        final Map<String, Object> pageState;
        if (!request.recentBreadcrumbs().isEmpty() && !mergedPageState.containsKey("recentBreadcrumbs")) {
            Map<String, Object> withBreadcrumbs = new LinkedHashMap<>(mergedPageState);
            withBreadcrumbs.put("recentBreadcrumbs", request.recentBreadcrumbs());
            pageState = withBreadcrumbs;
        } else {
            pageState = mergedPageState;
        }

        SecurityContext securityContext = SecurityContextHolder.getContext();
        UUID tenantId = TenantContext.getTenantId().orElse(null);
        UUID userId = TenantContext.getUserId().orElse(null);
        UUID warehouseId = TenantContext.getWarehouseId().orElse(null);
        List<UUID> authorizedWarehouses = TenantContext.getAuthorizedWarehouseIds();

        executor.execute(() -> {
            try {
                SecurityContextHolder.setContext(securityContext);
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }
                if (userId != null) {
                    TenantContext.setUserId(userId);
                }
                if (warehouseId != null) {
                    TenantContext.setWarehouseId(warehouseId);
                }
                if (authorizedWarehouses != null && !authorizedWarehouses.isEmpty()) {
                    TenantContext.setAuthorizedWarehouseIds(authorizedWarehouses);
                }
                supportChatService.streamAnswer(
                        request.message(),
                        roleSnapshot,
                        route,
                        request.pageContext(),
                        pageState,
                        request.imageBase64(),
                        request.imageMimeType(),
                        token -> {
                            try {
                                emitter.send(SseEmitter.event().name("token").data(token));
                            } catch (IOException ex) {
                                emitter.completeWithError(ex);
                            }
                        },
                        action -> {
                            try {
                                emitter.send(SseEmitter.event().name("action").data(toActionMap(action)));
                            } catch (IOException ex) {
                                emitter.completeWithError(ex);
                            }
                        },
                        reply -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data(toDoneMap(reply)));
                                emitter.complete();
                            } catch (IOException ex) {
                                emitter.completeWithError(ex);
                            }
                        });
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        });

        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    @PostMapping("/actions/execute")
    public Map<String, Object> execute(@RequestBody ExecuteActionRequest request) {
        return supportChatService.executeAction(
                request.action(),
                request.params() == null ? Map.of() : request.params());
    }

    @PostMapping("/actions/draft-execute")
    public Map<String, Object> executeDraft(@RequestBody ExecuteDraftRequest request) {
        return supportChatService.executeDraft(request.actionDraft());
    }

    static Map<String, Object> toActionMap(SupportActionProposal action) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", action.type());
        map.put("action", action.action());
        map.put("label", action.label());
        map.put("params", action.params());
        if (!action.target().isBlank()) {
            map.put("target", action.target());
        }
        return map;
    }

    static Map<String, Object> toDoneMap(SupportStructuredReply reply) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", true);
        map.put("replyMarkdown", reply.replyMarkdown());
        map.put("followUpQuestions", reply.followUpQuestions());
        List<Map<String, Object>> chips = new ArrayList<>();
        for (SupportActionProposal action : reply.actionChips()) {
            chips.add(toActionMap(action));
        }
        map.put("actionChips", chips);
        if (reply.actionDraft() != null) {
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("title", reply.actionDraft().title());
            draft.put("description", reply.actionDraft().description());
            draft.put("targetEndpoint", reply.actionDraft().targetEndpoint());
            draft.put("httpMethod", reply.actionDraft().httpMethod());
            draft.put("payload", reply.actionDraft().payload());
            map.put("actionDraft", draft);
        }
        if (reply.proactiveInsight() != null) {
            map.put("proactiveInsight", reply.proactiveInsight());
        }
        return map;
    }

    private static String resolveRoute(
            String routeHeader,
            Map<String, Object> routeContext,
            Map<String, Object> pageState
    ) {
        if (routeHeader != null && !routeHeader.isBlank()) {
            return routeHeader;
        }
        if (routeContext != null) {
            Object path = routeContext.get("pathname");
            Object search = routeContext.get("search");
            if (path != null) {
                String s = search == null ? "" : String.valueOf(search);
                return String.valueOf(path) + s;
            }
        }
        if (pageState != null && pageState.get("routePath") != null) {
            return String.valueOf(pageState.get("routePath"));
        }
        return "";
    }

    private static Map<String, Object> mergePageState(
            Map<String, Object> pageState,
            List<String> roles,
            String route
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (pageState != null) {
            for (Map.Entry<String, Object> entry : pageState.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (!merged.containsKey("userRoles")) {
            merged.put("userRoles", roles);
        }
        if (!merged.containsKey("routePath") || String.valueOf(merged.get("routePath")).isBlank()) {
            merged.put("routePath", route == null ? "" : route);
        }
        return Map.copyOf(merged);
    }

    private static List<String> rolesFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String a = authority.getAuthority();
            if (a != null && a.startsWith("ROLE_")) {
                roles.add(a.substring(5));
            } else if (a != null) {
                roles.add(a);
            }
        }
        return SupportChatService.normalizeRoles(roles);
    }
}
