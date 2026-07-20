package com.invsys.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invsys.support.ai")
public class SupportAiProperties {

    /** When false, chat endpoint returns 503. */
    private boolean enabled = true;

    /**
     * {@code heuristic} = grounded composer from pgvector fragments (default / CI).
     * {@code openai} = Spring AI ChatClient against OpenAI when API key is set.
     */
    private String llm = "heuristic";

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
