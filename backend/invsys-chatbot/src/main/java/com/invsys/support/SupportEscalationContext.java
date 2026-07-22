package com.invsys.support;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Request-scoped escalation + chat session metadata for Support Co-Pilot tools/advisors.
 */
@Component
public class SupportEscalationContext {

    private final ThreadLocal<EscalationCard> card = new ThreadLocal<>();
    private final ThreadLocal<String> sessionId = new ThreadLocal<>();
    private final ThreadLocal<String> route = new ThreadLocal<>();
    private final ThreadLocal<String> role = new ThreadLocal<>();

    public void begin(String sessionId, String route, String role) {
        clear();
        if (sessionId != null && !sessionId.isBlank()) {
            this.sessionId.set(sessionId);
        }
        if (route != null && !route.isBlank()) {
            this.route.set(route);
        }
        if (role != null && !role.isBlank()) {
            this.role.set(role);
        }
    }

    public void setCard(EscalationCard escalationCard) {
        card.set(escalationCard);
    }

    public Optional<EscalationCard> consumeCard() {
        EscalationCard value = card.get();
        card.remove();
        return Optional.ofNullable(value);
    }

    public Optional<String> currentSessionId() {
        return Optional.ofNullable(sessionId.get());
    }

    public Optional<String> currentRoute() {
        return Optional.ofNullable(route.get());
    }

    public Optional<String> currentRole() {
        return Optional.ofNullable(role.get());
    }

    public void clear() {
        card.remove();
        sessionId.remove();
        route.remove();
        role.remove();
    }

    public record EscalationCard(String ticketId, String status, String message) {
    }
}
