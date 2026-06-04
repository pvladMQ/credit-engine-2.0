package com.tanzu.creditengine.service;

import com.tanzu.creditengine.ai.CreditTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Cloud profile: natural-language querying via Spring AI tool-calling against the Tanzu
 * {@code credit-chat} GenAI service. The model selects and parameterizes {@link CreditTools}
 * methods; we drain the rows those tools produced to render the operator's table.
 */
@Service
@Profile("cloud")
public class SpringAiQueryService implements AiQueryService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiQueryService.class);

    private final ChatClient chatClient;
    private final CreditTools creditTools;
    private final SettingsService settings;

    public SpringAiQueryService(ChatClient chatClient, CreditTools creditTools, SettingsService settings) {
        this.chatClient = chatClient;
        this.creditTools = creditTools;
        this.settings = settings;
    }

    @Override
    public AiAnswer answer(String prompt) {
        if (!settings.current().isAiEnabled()) {
            return AiAnswer.message("The AI assistant is currently disabled in the admin portal.");
        }
        creditTools.begin();
        try {
            String text = chatClient.prompt()
                    .user(prompt)
                    .tools(creditTools)
                    .call()
                    .content();
            List<Map<String, Object>> rows = creditTools.drain();
            return new AiAnswer(text, rows);
        } catch (RuntimeException ex) {
            log.error("GenAI query failed", ex);
            creditTools.drain();
            return AiAnswer.message("The AI service is currently unavailable: " + ex.getMessage());
        }
    }
}
