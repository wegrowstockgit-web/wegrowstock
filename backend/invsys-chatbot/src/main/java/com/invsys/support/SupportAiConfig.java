package com.invsys.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SupportAiProperties.class)
public class SupportAiConfig {
}
