package com.invsys.support;

import com.invsys.chatbot.service.QueryRewriterService;
import com.invsys.support.advisor.OperationalInstructorValidationAdvisor;
import com.invsys.support.dto.ActionDraft;
import com.invsys.support.dto.SupportChatResponse;
import com.invsys.support.tools.SupportCopilotReadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Agentic GraphRAG orchestrator with CQRS tools, bottleneck insights, drafts, and vision.
 */
@Service
public class SupportChatService {

    private static final Logger log = LoggerFactory.getLogger(SupportChatService.class);

    private final SupportAiProperties properties;
    private final SupportKnowledgeRepository repository;
    private final SupportGraphRepository graphRepository;
    private final EmbeddingModel embeddingModel;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final ObjectProvider<ChatModel> chatModel;
    private final SupportAgentTools agentTools;
    private final SupportEscalationTools escalationTools;
    private final SupportEscalationContext escalationContext;
    private final SupportCopilotReadService readService;
    private final SupportBottleneckService bottleneckService;
    private final SupportActionDraftExecutor draftExecutor;
    private final List<ToolCallback> readToolCallbacks;
    private final ObjectProvider<VectorStore> vectorStore;
    private final ObjectProvider<ChatMemory> chatMemory;
    private final ObjectProvider<QueryRewriterService> queryRewriter;

    public SupportChatService(
            SupportAiProperties properties,
            SupportKnowledgeRepository repository,
            SupportGraphRepository graphRepository,
            EmbeddingModel embeddingModel,
            ObjectProvider<ChatClient.Builder> chatClientBuilder,
            ObjectProvider<ChatModel> chatModel,
            SupportAgentTools agentTools,
            SupportEscalationTools escalationTools,
            SupportEscalationContext escalationContext,
            SupportCopilotReadService readService,
            SupportBottleneckService bottleneckService,
            SupportActionDraftExecutor draftExecutor,
            @Qualifier("supportCopilotReadToolCallbacks") ObjectProvider<List<ToolCallback>> readToolCallbacks,
            ObjectProvider<VectorStore> vectorStore,
            ObjectProvider<ChatMemory> chatMemory,
            ObjectProvider<QueryRewriterService> queryRewriter
    ) {
        this.properties = properties;
        this.repository = repository;
        this.graphRepository = graphRepository;
        this.embeddingModel = embeddingModel;
        this.chatClientBuilder = chatClientBuilder;
        this.chatModel = chatModel;
        this.agentTools = agentTools;
        this.escalationTools = escalationTools;
        this.escalationContext = escalationContext;
        this.readService = readService;
        this.bottleneckService = bottleneckService;
        this.draftExecutor = draftExecutor;
        List<ToolCallback> callbacks = readToolCallbacks.getIfAvailable();
        this.readToolCallbacks = callbacks == null ? List.of() : List.copyOf(callbacks);
        this.vectorStore = vectorStore;
        this.chatMemory = chatMemory;
        this.queryRewriter = queryRewriter;
    }

    public void streamAnswer(
            String message,
            List<String> roles,
            String route,
            Consumer<String> onToken,
            Consumer<SupportActionProposal> onAction,
            Consumer<SupportStructuredReply> onComplete
    ) {
        streamAnswer(message, roles, route, Map.of(), Map.of(), null, null, null, onToken, onAction, onComplete);
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
        streamAnswer(message, roles, route, pageContext, Map.of(), null, null, null, onToken, onAction, onComplete);
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
        streamAnswer(message, roles, route, pageContext, pageState, null, null, null, onToken, onAction, onComplete);
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
        streamAnswer(message, roles, route, pageContext, pageState, imageBase64, imageMimeType, null,
                onToken, onAction, onComplete);
    }

    public void streamAnswer(
            String message,
            List<String> roles,
            String route,
            Map<String, Object> pageContext,
            Map<String, Object> pageState,
            String imageBase64,
            String imageMimeType,
            String sessionId,
            Consumer<String> onToken,
            Consumer<SupportActionProposal> onAction,
            Consumer<SupportStructuredReply> onComplete
    ) {
        try {
            streamAnswerInternal(
                    message, roles, route, pageContext, pageState, imageBase64, imageMimeType, sessionId,
                    onToken, onAction, onComplete);
        } finally {
            escalationContext.clear();
        }
    }

    private void streamAnswerInternal(
            String message,
            List<String> roles,
            String route,
            Map<String, Object> pageContext,
            Map<String, Object> pageState,
            String imageBase64,
            String imageMimeType,
            String sessionId,
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
        String conversationId = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId.trim();
        String primaryRole = normalizedRoles.isEmpty() ? "" : normalizedRoles.getFirst();
        escalationContext.begin(conversationId, route, primaryRole);

        String userQuery = extractUserQueryTail(question);
        String embedText = rewriteQueryForRetrieval(userQuery);
        float[] embedding = embeddingModel.embed(embedText);
        // Hybrid dense + tsvector sparse search fused with RRF (k=60); sparse uses raw user query.
        List<SupportKnowledgeChunk> seeds = repository.searchHybrid(
                embedding, userQuery, normalizedRoles, route, properties.getTopK());
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
            liveFacts = readService.formatLiveFactsForPrompt(userQuery, warehouseHint);
        } catch (RuntimeException ignored) {
            // Tenant missing or lookup failure — continue with playbook-only guidance.
        }

        String system = SupportSystemPromptBuilder.build(
                        normalizedRoles, route, retrieved, safePageContext, safePageState)
                + """

                You are Gemini 2.5 Flash operating as the Growstock Inventory Level-1/Level-2 Support Agent.
                Rely STRICTLY on retrieved SOP / RAG context and live CQRS facts. If the answer is not in the \
                retrieved manuals, say you do not know and call escalateToHumanSupport.
                If the user is frustrated, asks for a human, or RAG context is insufficient, you MUST invoke \
                the escalateToHumanSupport tool.
                Prefer manuals matching the activeRoute (pageState.pathname / route) and userRole.
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

        ChatClient.Builder builder = resolveChatClientBuilder();
        if (usesChatClientLlm() && builder != null) {
            log.info(
                    "Support chat path=gemini llm={} chatModel={} embeddingModel={}",
                    properties.getLlm(),
                    chatModel.getIfAvailable() == null
                            ? "unknown"
                            : chatModel.getIfAvailable().getClass().getSimpleName(),
                    embeddingModel.getClass().getSimpleName());
            // @Tool beans via defaultTools; FunctionToolCallback list via defaultToolCallbacks.
            builder = builder
                    .defaultSystem(system)
                    .defaultTools(agentTools, escalationTools);
            if (!readToolCallbacks.isEmpty()) {
                builder = builder.defaultToolCallbacks(readToolCallbacks);
            }
            VectorStore store = vectorStore.getIfAvailable();
            ChatMemory memory = chatMemory.getIfAvailable();
            // Recursive tool-call loop, then memory / RAG, then instructor-shape validation.
            builder = builder.defaultAdvisors(
                    ToolCallAdvisor.builder().conversationHistoryEnabled(true).build(),
                    new OperationalInstructorValidationAdvisor());
            // Optional second vector search — off by default; hybrid GraphRAG already populated the system prompt.
            if (properties.isQuestionAnswerAdvisorEnabled() && store != null) {
                builder = builder.defaultAdvisors(
                        QuestionAnswerAdvisor.builder(store)
                                .searchRequest(SearchRequest.builder().topK(Math.min(4, properties.getTopK())).build())
                                .build());
            }
            if (memory != null) {
                builder = builder.defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(memory).build());
            }
            ChatClient client = builder.build();
            SupportChatResponse structured = null;
            String content = null;
            boolean usedGeminiText = false;
            try {
                if (hasImage) {
                    content = callWithImage(client, question, imageBase64, imageMimeType, conversationId);
                    usedGeminiText = content != null && !content.isBlank();
                } else {
                    structured = withDiagnosticTools(client.prompt()
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                            .user(question))
                            .call()
                            .entity(SupportChatResponse.class);
                    usedGeminiText = structured != null
                            && structured.replyMarkdown() != null
                            && !structured.replyMarkdown().isBlank();
                }
            } catch (RuntimeException entityFailed) {
                if (SupportAiErrorClassifier.isQuotaOrRateLimit(entityFailed)) {
                    // Do not burn a second Gemini call (or wait through more retries) on free-tier 429s.
                    log.warn(
                            "Support chat Gemini quota/rate-limit; failing fast to heuristic: {}",
                            summarizeAiError(entityFailed));
                } else {
                    log.warn(
                            "Support chat Gemini structured call failed; retrying plain content: {}",
                            summarizeAiError(entityFailed));
                    try {
                        if (hasImage) {
                            content = callWithImage(client, question, imageBase64, imageMimeType, conversationId);
                        } else {
                            content = withDiagnosticTools(client.prompt()
                                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                                    .user(question))
                                    .call()
                                    .content();
                        }
                        usedGeminiText = content != null && !content.isBlank();
                    } catch (RuntimeException contentFailed) {
                        if (SupportAiErrorClassifier.isQuotaOrRateLimit(contentFailed)) {
                            log.warn(
                                    "Support chat Gemini quota/rate-limit on content retry; heuristic fallback: {}",
                                    summarizeAiError(contentFailed));
                        } else {
                            log.warn(
                                    "Support chat Gemini content call failed; falling back to heuristic: {}",
                                    summarizeAiError(contentFailed));
                        }
                        content = null;
                    }
                }
            }

            HeuristicSupportResult side = HeuristicSupportComposer.compose(
                    question, normalizedRoles, route, retrieved, system, safePageState);
            SupportStructuredReply reply;
            if (structured != null && structured.replyMarkdown() != null && !structured.replyMarkdown().isBlank()) {
                reply = mergeStructured(structured.toStructuredReply(), side);
                log.info("Support chat replySource=gemini-structured");
            } else {
                String markdown = content == null || content.isBlank() ? side.answer() : content;
                if (!usedGeminiText) {
                    log.warn("Support chat replySource=heuristic-fallback (Gemini returned empty)");
                } else {
                    log.info("Support chat replySource=gemini-content");
                }
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
            reply = attachEscalation(reply);
            streamChunks(reply.replyMarkdown(), onToken);
            for (SupportActionProposal action : reply.actionChips()) {
                onAction.accept(action);
            }
            onComplete.accept(reply);
            return;
        }

        ChatModel availableModel = null;
        try {
            availableModel = chatModel.getIfAvailable();
        } catch (RuntimeException ignored) {
            // prototype ChatModel creation can fail when no provider key is configured
        }
        log.info(
                "Support chat path=heuristic llm={} chatClientBuilder={} chatModel={} embeddingModel={}",
                properties.getLlm(),
                resolveChatClientBuilder() != null,
                availableModel == null ? "absent" : availableModel.getClass().getSimpleName(),
                embeddingModel.getClass().getSimpleName());
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
        reply = attachEscalation(reply);
        streamChunks(reply.replyMarkdown(), onToken);
        for (SupportActionProposal action : reply.actionChips()) {
            onAction.accept(action);
        }
        onComplete.accept(reply);
    }

    private static String summarizeAiError(Throwable error) {
        Throwable cursor = error;
        String best = error.toString();
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && (message.contains("429")
                    || message.contains("quota")
                    || message.contains("API_KEY")
                    || message.contains("PERMISSION")
                    || message.contains("404")
                    || message.contains("model"))) {
                return cursor.getClass().getSimpleName() + ": " + message;
            }
            best = cursor.getClass().getSimpleName() + ": " + (message == null ? cursor.toString() : message);
            cursor = cursor.getCause();
        }
        return best;
    }

    /**
     * Prefer Spring AI's prototype {@link ChatClient.Builder} when present; otherwise build from
     * {@link ChatModel} (Google GenAI {@code gemini-2.5-flash}).
     */
    private ChatClient.Builder resolveChatClientBuilder() {
        try {
            ChatClient.Builder fromAuto = chatClientBuilder.getIfAvailable();
            if (fromAuto != null) {
                return fromAuto;
            }
        } catch (RuntimeException ex) {
            log.warn("ChatClient.Builder ObjectProvider failed: {}", ex.toString());
        }
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return null;
        }
        log.info("Building ChatClient from ChatModel {}", model.getClass().getSimpleName());
        return ChatClient.builder(model);
    }

    private SupportStructuredReply attachEscalation(SupportStructuredReply reply) {
        return escalationContext.consumeCard()
                .map(card -> reply.withEscalation(new SupportStructuredReply.EscalationCard(
                        card.ticketId(), card.status(), card.message())))
                .orElse(reply);
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
        // Tools are already bound on the ChatClient via defaultTools / defaultToolCallbacks.
        // Re-attaching them here duplicates names and fails ToolCallingChatOptions.
        return prompt;
    }

    private String callWithImage(
            ChatClient client,
            String question,
            String imageBase64,
            String imageMimeType,
            String conversationId
    ) {
        byte[] bytes = decodeImage(imageBase64);
        if (bytes.length == 0) {
            return withDiagnosticTools(client.prompt()
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .user(question))
                    .call()
                    .content();
        }
        String mime = imageMimeType == null || imageMimeType.isBlank() ? "image/jpeg" : imageMimeType;
        Media media = new Media(
                MimeTypeUtils.parseMimeType(mime),
                new ByteArrayResource(bytes));
        UserMessage userMessage = UserMessage.builder()
                .text(question)
                .media(media)
                .build();
        return withDiagnosticTools(client.prompt(new Prompt(List.of(userMessage)))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)))
                .call()
                .content();
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

    /**
     * HyDE: embed a hypothetical SOP answer when enabled and {@link QueryRewriterService} is available.
     */
    public String rewriteQueryForRetrieval(String userQuery) {
        if (!properties.isHydeEnabled()) {
            return userQuery;
        }
        QueryRewriterService rewriter = queryRewriter == null ? null : queryRewriter.getIfAvailable();
        if (rewriter == null) {
            return userQuery;
        }
        try {
            String rewritten = rewriter.rewriteForRetrieval(userQuery);
            return rewritten == null || rewritten.isBlank() ? userQuery : rewritten;
        } catch (RuntimeException ex) {
            if (SupportAiErrorClassifier.isQuotaOrRateLimit(ex)) {
                log.warn("HyDE skipped due to quota/rate-limit; using raw query: {}", summarizeAiError(ex));
            } else {
                log.debug("HyDE rewrite skipped: {}", ex.toString());
            }
            return userQuery;
        }
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
