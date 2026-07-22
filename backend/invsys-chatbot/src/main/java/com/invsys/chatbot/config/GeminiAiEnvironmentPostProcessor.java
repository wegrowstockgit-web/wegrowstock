package com.invsys.chatbot.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * When {@code GEMINI_API_KEY} is absent, force {@code spring.ai.model.*=none} and Support LLM to
 * {@code heuristic}. Required because Spring AI Google GenAI autoconfig uses
 * {@code matchIfMissing=true} on {@code spring.ai.model.chat}.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class GeminiAiEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE = "invsysGeminiAiKeyDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(resolveGeminiApiKey(environment))) {
            return;
        }

        Map<String, Object> defaults = new HashMap<>();
        putIfEnvUnset(defaults, "SPRING_AI_MODEL_CHAT", "spring.ai.model.chat", "none");
        putIfEnvUnset(defaults, "SPRING_AI_MODEL_EMBEDDING_TEXT", "spring.ai.model.embedding.text", "none");
        putIfEnvUnset(defaults, "SUPPORT_AI_LLM", "invsys.support.ai.llm", "heuristic");
        if (defaults.isEmpty()) {
            return;
        }

        // Highest precedence so we beat application.yml defaults (google-genai / gemini).
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, defaults));
    }

    private static String resolveGeminiApiKey(ConfigurableEnvironment environment) {
        String fromEnv = System.getenv("GEMINI_API_KEY");
        if (StringUtils.hasText(fromEnv)) {
            return fromEnv;
        }
        String fromProperty = environment.getProperty("spring.ai.google.genai.api-key", "");
        return StringUtils.hasText(fromProperty) ? fromProperty : "";
    }

    private static void putIfEnvUnset(
            Map<String, Object> defaults,
            String envName,
            String propertyName,
            String value
    ) {
        if (StringUtils.hasText(System.getenv(envName))) {
            return;
        }
        defaults.put(propertyName, value);
    }
}
