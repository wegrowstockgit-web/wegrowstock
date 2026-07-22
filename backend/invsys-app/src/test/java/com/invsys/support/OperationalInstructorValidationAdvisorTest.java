package com.invsys.support;

import com.invsys.support.advisor.OperationalInstructorValidationAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalInstructorValidationAdvisorTest {

    private final OperationalInstructorValidationAdvisor advisor = new OperationalInstructorValidationAdvisor();

    @Test
    void isValidRequiresFourInstructorHeadings() {
        assertThat(OperationalInstructorValidationAdvisor.isValid("""
                **Operational Diagnosis:** Stock is short.

                **Action Plan**
                1. Open **Sales Orders**.

                **↺ Ledger Safety & Reversal Rule**
                Use Un-allocate.

                **👥 Downstream Impact**
                Pickers get new tasks.
                """)).isTrue();
        assertThat(OperationalInstructorValidationAdvisor.isValid("Just click allocate.")).isFalse();
    }

    @Test
    void adviseCallReformattingWhenHeadingsMissing() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("Click Un-allocate on the order."))))
                .build();
        when(chain.nextCall(any())).thenReturn(new ChatClientResponse(chatResponse, Map.of()));

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("how do I undo allocation?")))
                .build();

        ChatClientResponse out = advisor.adviseCall(request, chain);
        String text = out.chatResponse().getResult().getOutput().getText();
        assertThat(text).contains("**Operational Diagnosis");
        assertThat(text).contains("**Action Plan");
        assertThat(text).containsIgnoringCase("Ledger Safety");
        assertThat(text).containsIgnoringCase("Downstream Impact");
    }
}
