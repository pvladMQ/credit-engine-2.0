package com.tanzu.creditengine.config;

import io.pivotal.cfenv.boot.genai.GenaiLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Builds the Spring AI {@link ChatClient} for the Tanzu GenAI {@code credit-chat} service.
 *
 * <p>The {@link GenaiLocator} (contributed by java-cfenv-boot-tanzu-genai) reads the bound
 * {@code ai-models} service from VCAP_SERVICES — handling the CredHub-backed, multi-model
 * {@code endpoint} credential format — and returns a fully-configured Spring AI
 * {@code ChatModel} pointed at the locally-hosted model (e.g. gpt-oss). We prefer a
 * tool-capable model since the assistant uses {@code @Tool} function calling.
 *
 * Cloud profile only — the local profile answers with a deterministic stub.
 */
@Configuration
@Profile("cloud")
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    private static final String SYSTEM_PROMPT = """
            You are the credit-chat assistant for a credit scoring engine.
            Answer questions about calculated credit scores by calling the provided tools.
            Always prefer a tool over guessing. Never fabricate scores or SSNs.
            Keep answers concise; the structured rows are shown to the operator separately.
            """;

    @Bean
    public ChatClient creditChatClient(GenaiLocator genaiLocator) {
        ChatModel chatModel;
        try {
            // Prefer a model that advertises tool/function-calling (plan: chat-and-tools-model).
            chatModel = genaiLocator.getFirstAvailableToolModel();
            log.info("GenAI: using first available tool-capable chat model from the credit-chat binding");
        } catch (RuntimeException ex) {
            // Fall back to any available chat model so the app still starts and answers
            // (tool calls just won't fire if the model can't do them).
            log.warn("GenAI: no tool-capable model found ({}); falling back to first available chat model",
                    ex.getMessage());
            chatModel = genaiLocator.getFirstAvailableChatModel();
        }
        return ChatClient.builder(chatModel).defaultSystem(SYSTEM_PROMPT).build();
    }
}
