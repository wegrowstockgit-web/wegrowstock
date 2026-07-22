package com.invsys.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invsys.support.ai")
public class SupportAiProperties {

    /** When false, chat endpoint returns 503. */
    private boolean enabled = true;

    /**
     * {@code gemini} = Spring AI ChatClient against Google GenAI Gemini 2.5 Flash
     * (requires {@code spring.ai.model.chat=google-genai} + {@code GEMINI_API_KEY}).
     * {@code heuristic} = grounded composer from pgvector fragments (CI / no API key).
     * {@code openai} = Spring AI ChatClient against OpenAI when API key is set.
     */
    private String llm = "gemini";

    private int topK = 6;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLlm() {
        return llm;
    }

    public void setLlm(String llm) {
        this.llm = llm;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
