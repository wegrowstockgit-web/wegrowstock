package com.invsys.api;

import com.invsys.support.SupportActionProposal;
import com.invsys.support.SupportChatService;
import com.invsys.support.SupportStructuredReply;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Streaming support chat — GraphRAG + agentic action proposals; confirm execute endpoint.
 */
@RestController
@RequestMapping("/api/v1/support")
public class SupportChatController {

    private final SupportChatService supportChatService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SupportChatController(SupportChatService supportChatService) {
        this.supportChatService = supportChatService;
    }

    public record ChatRequest(
            @NotBlank String message,
            Map<String, Object> pageContext,
            Map<String, Object> routeContext,
            Map<String, Object> pageState,
            List<String> userRoles
    ) {
        public ChatRequest {
            if (pageContext == null) {
                pageContext = Map.of();
            }
            if (routeContext == null) {
                routeContext = Map.of();
            }
            if (pageState == null) {
                pageState = Map.of();
            }
            if (userRoles == null) {
                userRoles = List.of();
            }
        }
    }

    public record ExecuteActionRequest(
            @NotBlank String action,
            Map<String, String> params
    ) {
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
        Map<String, Object> pageState = mergePageState(request.pageState(), roleSnapshot, route);

        executor.execute(() -> {
            try {
                supportChatService.streamAnswer(
                        request.message(),
                        roleSnapshot,
                        route,
                        request.pageContext(),
                        pageState,
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
                // Map.copyOf rejects null values — SPA snapshots often send null filters/tabs.
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
        if (auth == null || auth.getAuthorities() == null) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && value.startsWith("ROLE_")) {
                roles.add(value.substring("ROLE_".length()));
            }
        }
        return SupportChatService.normalizeRoles(roles);
    }
}
