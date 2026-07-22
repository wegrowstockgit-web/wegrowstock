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

    /** Keep prompts lean — hybrid GraphRAG already expands seeds. */
    private int topK = 4;

    /**
     * HyDE burns an extra Gemini call before retrieval. Off by default to preserve quota;
     * enable when embeddings are real (not hash) and queries are ambiguous.
     */
    private boolean hydeEnabled = false;

    /**
     * Spring AI {@code QuestionAnswerAdvisor} runs a second vector search on top of hybrid RAG.
     * Off by default — duplicates tokens and latency.
     */
    private boolean questionAnswerAdvisorEnabled = false;

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

    public boolean isHydeEnabled() {
        return hydeEnabled;
    }

    public void setHydeEnabled(boolean hydeEnabled) {
        this.hydeEnabled = hydeEnabled;
    }

    public boolean isQuestionAnswerAdvisorEnabled() {
        return questionAnswerAdvisorEnabled;
    }

    public void setQuestionAnswerAdvisorEnabled(boolean questionAnswerAdvisorEnabled) {
        this.questionAnswerAdvisorEnabled = questionAnswerAdvisorEnabled;
    }
}
