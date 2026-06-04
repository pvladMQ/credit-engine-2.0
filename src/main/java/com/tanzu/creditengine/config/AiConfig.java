package com.tanzu.creditengine.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Builds the Spring AI {@link ChatClient} for the Tanzu {@code credit-chat} GenAI service
 * (which exposes an OpenAI-compatible API consumed via spring-ai-starter-model-openai).
 * Cloud profile only — the local profile answers with a deterministic stub.
 */
@Configuration
@Profile("cloud")
public class AiConfig {

    private static final String SYSTEM_PROMPT = """
            You are the credit-chat assistant for a credit scoring engine.
            Answer questions about calculated credit scores by calling the provided tools.
            Always prefer a tool over guessing. Never fabricate scores or SSNs.
            Keep answers concise; the structured rows are shown to the operator separately.
            """;

    @Bean
    public ChatClient creditChatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(SYSTEM_PROMPT).build();
    }
}
