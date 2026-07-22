package com.invsys.support.advisor;

import com.invsys.support.OperationsInstructorFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Ensures Support Co-Pilot replies include the four mandatory Operational Instructor sections.
 * When the model omits a heading, reformats via {@link OperationsInstructorFormatter}.
 */
public class OperationalInstructorValidationAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(OperationalInstructorValidationAdvisor.class);

    @Override
    public String getName() {
        return "OperationalInstructorValidationAdvisor";
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return ensureValid(chain.nextCall(request), request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        // Avoid reformatting partial tokens; CallAdvisor covers .call() entity/content paths.
        return chain.nextStream(request);
    }

    private ChatClientResponse ensureValid(ChatClientResponse response, ChatClientRequest request) {
        String text = extractText(response);
        if (text == null || text.isBlank() || isValid(text)) {
            return response;
        }
        log.warn("Support reply missing mandatory instructor headings; reformatting");
        String userQuestion = "";
        try {
            if (request.prompt() != null && request.prompt().getUserMessage() != null) {
                userQuestion = request.prompt().getUserMessage().getText();
            }
        } catch (RuntimeException ignored) {
            // keep empty question
        }
        String reformatted = OperationsInstructorFormatter.enrich(
                text, userQuestion, List.of(), "", null);
        if (reformatted == null || reformatted.isBlank()) {
            return response;
        }
        Generation generation = new Generation(new AssistantMessage(reformatted));
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(generation))
                .build();
        return response.mutate().chatResponse(chatResponse).build();
    }

    /** Visible for unit tests. */
    public static boolean isValid(String markdown) {
        if (markdown == null) {
            return false;
        }
        boolean diagnosis = markdown.contains("**Operational Diagnosis");
        boolean actionPlan = markdown.contains("**Action Plan");
        boolean ledger = markdown.contains("Ledger Safety") || markdown.contains("Reversal Rule");
        boolean downstream = markdown.contains("Downstream Impact");
        return diagnosis && actionPlan && ledger && downstream;
    }

    private static String extractText(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null) {
            return null;
        }
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }
}
