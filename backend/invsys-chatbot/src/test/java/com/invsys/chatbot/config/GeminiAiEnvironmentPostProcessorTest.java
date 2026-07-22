package com.invsys.chatbot.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiAiEnvironmentPostProcessorTest {

    @Test
    void withoutApiKey_forcesHeadlessDefaultsAboveYaml() {
        Assumptions.assumeFalse(StringUtils.hasText(System.getenv("GEMINI_API_KEY")));

        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ai.google.genai.api-key", "");
        env.setProperty("spring.ai.model.chat", "google-genai");
        env.setProperty("spring.ai.model.embedding.text", "google-genai");
        env.setProperty("invsys.support.ai.llm", "gemini");

        new GeminiAiEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getPropertySources().contains(GeminiAiEnvironmentPostProcessor.PROPERTY_SOURCE)).isTrue();
        assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(env.getProperty("spring.ai.model.embedding.text")).isEqualTo("none");
        assertThat(env.getProperty("invsys.support.ai.llm")).isEqualTo("heuristic");
    }

    @Test
    void withApiKey_leavesYamlDefaultsAlone() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.ai.google.genai.api-key", "test-key");
        env.setProperty("spring.ai.model.chat", "google-genai");
        env.setProperty("spring.ai.model.embedding.text", "google-genai");
        env.setProperty("invsys.support.ai.llm", "gemini");

        new GeminiAiEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getPropertySources().contains(GeminiAiEnvironmentPostProcessor.PROPERTY_SOURCE)).isFalse();
        assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("google-genai");
        assertThat(env.getProperty("spring.ai.model.embedding.text")).isEqualTo("google-genai");
        assertThat(env.getProperty("invsys.support.ai.llm")).isEqualTo("gemini");
    }
}
