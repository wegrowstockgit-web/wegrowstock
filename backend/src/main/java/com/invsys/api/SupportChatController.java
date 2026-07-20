package com.invsys.api;

import com.invsys.support.SupportActionProposal;
import com.invsys.support.SupportChatService;
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
            Map<String, Object> pageContext
    ) {
        public ChatRequest {
            if (pageContext == null) {
                pageContext = Map.of();
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
        if (roles.isEmpty()) {
            roles = rolesFromSecurityContext();
        }
        String route = routeHeader == null ? "" : routeHeader;
        List<String> roleSnapshot = roles;

        executor.execute(() -> {
            try {
                supportChatService.streamAnswer(
                        request.message(),
                        roleSnapshot,
                        route,
                        request.pageContext(),
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
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data(Map.of("ok", true)));
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

    private static Map<String, Object> toActionMap(SupportActionProposal action) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", action.type());
        map.put("action", action.action());
        map.put("label", action.label());
        map.put("params", action.params());
        return map;
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
