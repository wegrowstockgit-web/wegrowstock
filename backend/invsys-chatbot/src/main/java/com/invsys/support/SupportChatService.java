package com.invsys.support;

import com.invsys.support.dto.ActionDraft;
import com.invsys.support.dto.SupportChatResponse;
import com.invsys.support.tools.SupportCopilotReadService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import com.invsys.domain.Tenant;
import com.invsys.domain.User;

/**
 * Agentic GraphRAG orchestrator with CQRS tools, bottleneck insights, drafts, and vision.
 */
@Service
public class SupportChatService {

    private final SupportAiProperties properties;
    private final SupportKnowledgeRepository repository;
    private final SupportGraphRepository graphRepository;
    private final EmbeddingModel embeddingModel;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final SupportAgentTools agentTools;
    private final SupportCopilotReadService readService;
    private final SupportBottleneckService bottleneckService;
    private final SupportActionDraftExecutor draftExecutor;
    private final List<ToolCallback> readToolCallbacks;

    public SupportChatService(
            SupportAiProperties properties,
            SupportKnowledgeRepository repository,
            SupportGraphRepository graphRepository,
            EmbeddingModel embeddingModel,
            ObjectProvider<ChatClient.Builder> chatClientBuilder,
            SupportAgentTools agentTools,
            SupportCopilotReadService readService,
            SupportBottleneckService bottleneckService,
            SupportActionDraftExecutor draftExecutor,
            @Qualifier("supportCopilotReadToolCallbacks") ObjectProvider<List<ToolCallback>> readToolCallbacks
    ) {
        this.properties = properties;
        this.repository = repository;
        this.graphRepository = graphRepository;
        this.embeddingModel = embeddingModel;
        this.chatClientBuilder = chatClientBuilder;
        this.agentTools = agentTools;
        this.readService = readService;
        this.bottleneckService = bottleneckService;
        this.draftExecutor = draftExecutor;
        List<ToolCallback> callbacks = readToolCallbacks.getIfAvailable();
        this.readToolCallbacks = callbacks == null ? List.of() : List.copyOf(callbacks);
    }

    public void streamAnswer(
            String message,
            List<String> roles,
            String route,
            Consumer<String> onToken,
            Consumer<SupportActionProposal> onAction,
            Consumer<SupportStructuredReply> onComplete
    ) {
        streamAnswer(message, roles, route, Map.of(), Map.of(), null, null, onToken, onAction, onComplete);
    }

    public void streamAnswer(
            String message,
            List<String> roles,
            String route,
            Map<String, Object> pageContext,
            Consumer<String> onToken,
            Consumer<SupportActionProposal> onAction,
            Consumer<SupportStructuredReply> onComplete
    ) {
        streamAnswer(message, roles, route, pageContext, Map.of(), null, null, onToken, onAction, onComplete);
    }

    public void streamAnswer(
            String message,
            List<String> roles,
            String route,
            Map<String, Object> pageContext,
            Map<String, Object> pageState,
            Consumer<String> onToken,
            Consumer<SupportActionProposal> onAction,
            Consumer<SupportStructuredReply> onComplete
    ) {
        streamAnswer(message, roles, route, pageContext, pageState, null, null, onToken, onAction, onComplete);
    }

    public void streamAnswer(
            String message,
            List<String> roles,
            String route,
            Map<String, Object> pageContext,
            Map<String, Object> pageState,
            String imageBase64,
            String imageMimeType,
            Consumer<String> onToken,
            Consumer<SupportActionProposal> onAction,
            Consumer<SupportStructuredReply> onComplete
    ) {
        if (!properties.isEnabled()) {
            String disabled = "Support assistant is disabled.";
            onToken.accept(disabled);
            onComplete.accept(SupportStructuredReply.of(disabled, List.of(), List.of()));
            return;
        }
        String question = message == null ? "" : message.trim();
        boolean hasImage = imageBase64 != null && !imageBase64.isBlank();
        if (question.isEmpty() && !hasImage) {
            String hint = "Ask a short operations question to get started.";
            onToken.accept(hint);
            onComplete.accept(SupportStructuredReply.of(hint, List.of(), List.of()));
            return;
        }
        if (question.isEmpty()) {
            question = "Please inspect this warehouse photo and tell me the safest next steps on screen.";
        }

        List<String> normalizedRoles = normalizeRoles(roles);
        String embedText = extractUserQueryTail(question);
        float[] embedding = embeddingModel.embed(embedText);
        List<SupportKnowledgeChunk> seeds = repository.searchSimilar(
                embedding, normalizedRoles, route, properties.getTopK());
        List<SupportKnowledgeChunk> retrieved = graphRepository.retrieveWithGraph(seeds, 2);
        Map<String, Object> safePageContext = pageContext == null ? Map.of() : pageContext;
        Map<String, Object> safePageState = pageState == null ? Map.of() : pageState;

        // Prefer live pageState.pathname for bottleneck pre-flight (sales-orders / fulfillment).
        String insightRoute = stringVal(safePageState.get("pathname"));
        if (insightRoute.isBlank()) {
            insightRoute = route;
        }
        String insight = null;
        try {
            insight = bottleneckService.detectProactiveInsight(insightRoute);
        } catch (RuntimeException ignored) {
            // Insights are best-effort.
        }

        String warehouseHint = stringVal(safePageState.get("activeWarehouseId"));
        String liveFacts = "";
        try {
            liveFacts = readService.formatLiveFactsForPrompt(embedText, warehouseHint);
        } catch (RuntimeException ignored) {
            // Tenant missing or lookup failure — continue with playbook-only guidance.
        }

        String system = SupportSystemPromptBuilder.build(
                        normalizedRoles, route, retrieved, safePageContext, safePageState)
                + """

                You are Gemini 2.0 Flash operating as the Growstock Inventory Co-Pilot.
                Return structured SupportChatResponse with replyMarkdown, optional proactiveInsight, \
                actionChips, optional actionDraft (title, description, targetEndpoint, httpMethod, payload), \
                and followUpQuestions.
                When guiding the user, generate actionable chips whenever appropriate \
                (e.g., NAVIGATE to relevant pages or SPOTLIGHT key on-screen elements). \
                Ensure target routes match valid application routes and SPOTLIGHT targets use exact \
                data-tour attribute selectors (e.g., [data-tour='btn-unallocate']). \
                Rewrite any retrieved tip into plain warehouse language. Never mention APIs, HTTP codes, \
                databases, or service class names. Emphasize safe undo with on-screen buttons \
                (Cancel, Un-allocate, Discard, stock correction) — never erase history.
                When a user asks to resolve an operational blocker (e.g., release unallocated stock, \
                cancel a backorder, or trigger a recount), do NOT just write instructions. Generate a \
                pre-filled actionDraft containing the exact targetEndpoint, httpMethod (POST/PATCH), \
                and payload so the user can execute it with one click. Prefer supportAction \
                generateCycleCount or releaseWave when those tools apply; otherwise use an allow-listed \
                /api/v1/... targetEndpoint with a clear title/description.
                """;
        if (!liveFacts.isBlank()) {
            system = system + "\nLive CQRS read-model facts (trust these over guesses):\n" + liveFacts + "\n";
        }
        if (hasImage) {
            system = system + """

                    If the user provides an image of a damaged barcode or shipping label, visually \
                    inspect it to extract the SKU, order number, or tracking details. Use your tools \
                    (checkOrderStatus, checkAvailableToPromise, getLedgerHistorySummary) to \
                    cross-reference the extracted data against the inventory ledger. Respond with \
                    non-technical numbered steps 1…N that use exact on-screen button names. Never \
                    invent serial/lot numbers that are not visible in the photo.
                    """;
        }

        if (usesChatClientLlm() && chatClientBuilder.getIfAvailable() != null) {
            // Bind read-only CQRS tools (Spring AI 1.1: defaultToolNames replaces defaultFunctions).
            ChatClient.Builder builder = chatClientBuilder.getObject()
                    .defaultSystem(system)
                    .defaultToolNames("checkOrderStatus", "getLedgerHistorySummary", "checkAvailableToPromise");
            if (!readToolCallbacks.isEmpty()) {
                builder = builder.defaultToolCallbacks(readToolCallbacks);
            }
            ChatClient client = builder.build();
            SupportChatResponse structured = null;
            String content = null;
            try {
                if (hasImage) {
                    content = callWithImage(client, question, imageBase64, imageMimeType);
                } else {
                    structured = withDiagnosticTools(client.prompt().user(question))
                            .call()
                            .entity(SupportChatResponse.class);
                }
            } catch (RuntimeException entityFailed) {
                try {
                    if (hasImage) {
                        content = callWithImage(client, question, imageBase64, imageMimeType);
                    } else {
                        content = withDiagnosticTools(client.prompt().user(question))
                                .call()
                                .content();
                    }
                } catch (RuntimeException ignored) {
                    content = null;
                }
            }

            HeuristicSupportResult side = HeuristicSupportComposer.compose(
                    question, normalizedRoles, route, retrieved, system, safePageState);
            SupportStructuredReply reply;
            if (structured != null && structured.replyMarkdown() != null && !structured.replyMarkdown().isBlank()) {
                reply = mergeStructured(structured.toStructuredReply(), side);
            } else {
                String markdown = content == null || content.isBlank() ? side.answer() : content;
                if (!liveFacts.isBlank() && !markdown.contains("available-to-promise") && !markdown.contains("Live ATP")) {
                    markdown = prependLiveFacts(markdown, liveFacts);
                }
                if (hasImage) {
                    markdown = prependVisionCoach(markdown);
                }
                reply = SupportStructuredReply.of(markdown, side.actions(), side.followUps())
                        .withActionDraft(suggestDraft(side.actions()));
            }
            reply = reply.withProactiveInsight(insight);
            if (reply.actionDraft() == null) {
                ActionDraft draft = suggestDraft(reply.actionChips());
                if (draft == null) {
                    draft = suggestDraftFromQuestion(question, safePageState);
                }
                reply = reply.withActionDraft(draft);
            }
            streamChunks(reply.replyMarkdown(), onToken);
            for (SupportActionProposal action : reply.actionChips()) {
                onAction.accept(action);
            }
            onComplete.accept(reply);
            return;
        }

        HeuristicSupportResult result = HeuristicSupportComposer.compose(
                question, normalizedRoles, route, retrieved, system, safePageState);
        String answer = result.answer();
        if (!liveFacts.isBlank()) {
            answer = prependLiveFacts(answer, liveFacts);
        }
        if (hasImage) {
            answer = prependVisionCoach(answer);
        }
        ActionDraft draft = suggestDraft(result.actions());
        if (draft == null) {
            draft = suggestDraftFromQuestion(question, safePageState);
        }
        SupportStructuredReply reply = SupportStructuredReply.of(answer, result.actions(), result.followUps())
                .withActionDraft(draft)
                .withProactiveInsight(insight);
        streamChunks(reply.replyMarkdown(), onToken);
        for (SupportActionProposal action : reply.actionChips()) {
            onAction.accept(action);
        }
        onComplete.accept(reply);
    }

    public String detectInsight(String route) {
        try {
            return bottleneckService.detectProactiveInsight(route);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public Map<String, Object> executeAction(String action, Map<String, String> params) {
        return agentTools.execute(action, params);
    }

    public Map<String, Object> executeDraft(ActionDraft draft) {
        return draftExecutor.execute(draft);
    }

    static ActionDraft suggestDraft(List<SupportActionProposal> actions) {
        if (actions == null) {
            return null;
        }
        for (SupportActionProposal action : actions) {
            if ("generateCycleCount".equals(action.action())) {
                String zone = action.params().getOrDefault("zoneId", "selected bin");
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("supportAction", "generateCycleCount");
                payload.put("zoneId", zone);
                return new ActionDraft(
                        "Generate cycle count for " + zone,
                        "Creates a count worksheet for " + zone
                                + ". On-hand does not change until counts are approved.",
                        "/api/v1/cycle-counts",
                        "POST",
                        payload);
            }
            if ("releaseWave".equals(action.action())) {
                String waveId = action.params().getOrDefault("waveId", "");
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("supportAction", "releaseWave");
                payload.put("waveId", waveId);
                return new ActionDraft(
                        "Release picking wave",
                        "Releases the wave so floor pickers can claim tasks on handhelds.",
                        "/api/v1/picking/waves/" + waveId + "/release",
                        "POST",
                        payload);
            }
        }
        return null;
    }

    /**
     * Heuristic ActionDraft when the user asks to resolve holds / un-allocate without an LLM draft.
     * Navigational drafts stay on the allow-list and never mutate stock until Approve.
     */
    static ActionDraft suggestDraftFromQuestion(String question, Map<String, Object> pageState) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String q = question.toLowerCase(Locale.ROOT);
        String orderId = stringVal(pageState == null ? null : pageState.get("selectedEntityId"));
        if (orderId.isBlank() && pageState != null) {
            orderId = stringVal(pageState.get("selectedEntity"));
        }
        if (q.contains("un-allocate") || q.contains("unallocate") || q.contains("release reserved")) {
            String endpoint = orderId.isBlank()
                    ? "/api/v1/sales-orders/allocate"
                    : "/api/v1/sales-orders/" + orderId + "/allocate";
            Map<String, Object> payload = new LinkedHashMap<>();
            if (!orderId.isBlank()) {
                payload.put("orderId", orderId);
            }
            payload.put("intent", "unallocate");
            return new ActionDraft(
                    "Un-allocate reserved stock",
                    orderId.isBlank()
                            ? "Opens the safe Un-allocate path so reserved units return to open stock. "
                                    + "Confirm on the Sales Orders screen after Approve."
                            : "Releases reserved stock for order " + orderId
                                    + " back to open stock. Confirm with the on-screen Un-allocate control after Approve.",
                    endpoint,
                    "POST",
                    payload);
        }
        return null;
    }

    /** OpenAI or Google GenAI Gemini ChatClient path (not the heuristic composer). */
    private boolean usesChatClientLlm() {
        String llm = properties.getLlm();
        return "openai".equalsIgnoreCase(llm)
                || "gemini".equalsIgnoreCase(llm)
                || "google-genai".equalsIgnoreCase(llm);
    }

    /**
     * Attaches confirm-before-execute agent tools plus read-only CQRS diagnostic tools
     * ({@code checkAvailableToPromise}, {@code checkOrderStatus}, {@code getLedgerHistorySummary}).
     */
    private ChatClient.ChatClientRequestSpec withDiagnosticTools(ChatClient.ChatClientRequestSpec prompt) {
        ChatClient.ChatClientRequestSpec withAgent = prompt.tools(agentTools);
        if (readToolCallbacks.isEmpty()) {
            return withAgent;
        }
        return withAgent.tools(readToolCallbacks.toArray(ToolCallback[]::new));
    }

    private String callWithImage(
            ChatClient client,
            String question,
            String imageBase64,
            String imageMimeType
    ) {
        byte[] bytes = decodeImage(imageBase64);
        if (bytes.length == 0) {
            return withDiagnosticTools(client.prompt().user(question)).call().content();
        }
        String mime = imageMimeType == null || imageMimeType.isBlank() ? "image/jpeg" : imageMimeType;
        Media media = new Media(
                MimeTypeUtils.parseMimeType(mime),
                new ByteArrayResource(bytes));
        UserMessage userMessage = UserMessage.builder()
                .text(question)
                .media(media)
                .build();
        return withDiagnosticTools(client.prompt(new Prompt(List.of(userMessage)))).call().content();
    }

    private static byte[] decodeImage(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            return new byte[0];
        }
        String raw = imageBase64;
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma > 0) {
            raw = raw.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException ex) {
            return new byte[0];
        }
    }

    private static SupportStructuredReply mergeStructured(
            SupportStructuredReply fromLlm,
            HeuristicSupportResult side
    ) {
        List<SupportActionProposal> chips = new ArrayList<>(fromLlm.actionChips());
        for (SupportActionProposal extra : side.actions()) {
            boolean exists = chips.stream().anyMatch(c ->
                    c.action().equals(extra.action()) && c.label().equals(extra.label()));
            if (!exists) {
                chips.add(extra);
            }
        }
        List<String> followUps = fromLlm.followUpQuestions().isEmpty()
                ? side.followUps()
                : fromLlm.followUpQuestions();
        ActionDraft draft = fromLlm.actionDraft() != null
                ? fromLlm.actionDraft()
                : suggestDraft(chips);
        return SupportStructuredReply.of(fromLlm.replyMarkdown(), chips, followUps)
                .withActionDraft(draft);
    }

    private static String prependLiveFacts(String answer, String liveFacts) {
        return """
                **Operational Diagnosis:** Live stock/order facts are available for this question.

                %s

                %s
                """.formatted(liveFacts, answer).strip();
    }

    private static String prependVisionCoach(String answer) {
        return """
                **Operational Diagnosis:** A photo was attached — treat label/damage cues as the source of truth.

                **Action Plan**
                1. Look for torn labels, missing serials, or wet/crushed packaging in the photo.
                2. On desktop, open **Inventory → Lots** (or Exceptions) to print a replacement or **Skip & Flag**.
                3. If the item is damaged, do **not** put it into sellable stock — quarantine or scrap with a manager.

                %s
                """.formatted(answer).strip();
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static String extractUserQueryTail(String message) {
        if (message == null) {
            return "";
        }
        String marker = "User Query:";
        int idx = message.lastIndexOf(marker);
        if (idx >= 0) {
            return message.substring(idx + marker.length()).trim();
        }
        return message.trim();
    }

    public static List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            out.add(role.trim().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(out);
    }

    public static List<String> parseRolesHeader(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return normalizeRoles(Arrays.asList(header.split("[,\\s]+")));
    }

    private static void streamChunks(String content, Consumer<String> onToken) {
        if (content == null || content.isEmpty()) {
            return;
        }
        String[] parts = content.split("(?<=\\s)");
        for (String part : parts) {
            if (!part.isEmpty()) {
                onToken.accept(part);
            }
        }
    }
}
