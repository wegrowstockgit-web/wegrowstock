package com.invsys.chatbot;

import com.invsys.api.SupportChatController;
import com.invsys.api.AdminChatbotIngestController;
import com.invsys.support.SupportAiProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Loads Support Co-Pilot / training backend beans only when the chatbot feature is enabled.
 * When {@code invsys.features.chatbot.enabled=false} or this module is omitted from the artifact,
 * no support beans are registered and {@code /api/v1/support/**} returns 404.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SupportAiProperties.class)
@ComponentScan(basePackages = {"com.invsys.support", "com.invsys.chatbot"})
@Import({SupportChatController.class, AdminChatbotIngestController.class})
public class ChatbotAutoConfiguration {
}
