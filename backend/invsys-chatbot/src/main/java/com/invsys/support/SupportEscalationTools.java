package com.invsys.support;

import com.invsys.core.tenancy.TenantContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Human handoff tool — creates a {@code support_tickets} row and stashes an escalation card
 * for the SSE {@code done} payload.
 */
@Component
public class SupportEscalationTools {

    private final JdbcTemplate jdbcTemplate;
    private final SupportEscalationContext escalationContext;

    public SupportEscalationTools(JdbcTemplate jdbcTemplate, SupportEscalationContext escalationContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.escalationContext = escalationContext;
    }

    @Tool(description = "Escalate the conversation to a human support agent. "
            + "MUST be called when the user is frustrated, explicitly asks for a human, "
            + "or the retrieved SOP/RAG context does not contain the answer. "
            + "Creates a support ticket and returns confirmation for the UI card.")
    public String escalateToHumanSupport(
            @ToolParam(description = "Short subject line for the ticket") String subject,
            @ToolParam(description = "Summary of the user problem and what was already tried") String summary,
            @ToolParam(description = "Priority: LOW, NORMAL, HIGH, or URGENT") String priority
    ) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.getUserId().orElse(null);
        String sessionId = escalationContext.currentSessionId().orElse(null);
        String route = escalationContext.currentRoute().orElse(null);
        String role = escalationContext.currentRole().orElse(null);
        String safeSubject = subject == null || subject.isBlank() ? "Support escalation" : subject.trim();
        String safeSummary = summary == null || summary.isBlank() ? "User requested human assistance." : summary.trim();
        String safePriority = normalizePriority(priority);

        UUID ticketId = jdbcTemplate.queryForObject("""
                INSERT INTO support_tickets (
                    tenant_id, opened_by, session_id, route, user_role, subject, summary, status, priority
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)
                RETURNING id
                """,
                UUID.class,
                tenantId,
                userId,
                sessionId,
                route,
                role,
                safeSubject,
                safeSummary,
                safePriority);

        SupportEscalationContext.EscalationCard card = new SupportEscalationContext.EscalationCard(
                ticketId == null ? UUID.randomUUID().toString() : ticketId.toString(),
                "OPEN",
                "Escalation successful — a human agent will follow up. Ticket "
                        + (ticketId == null ? "" : ticketId) + " is open.");
        escalationContext.setCard(card);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("type", "ESCALATION");
        payload.put("ticketId", card.ticketId());
        payload.put("status", card.status());
        payload.put("message", card.message());
        return toJson(payload);
    }

    private static String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "NORMAL";
        }
        String p = priority.trim().toUpperCase();
        return switch (p) {
            case "LOW", "NORMAL", "HIGH", "URGENT" -> p;
            default -> "NORMAL";
        };
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(esc(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(esc(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
