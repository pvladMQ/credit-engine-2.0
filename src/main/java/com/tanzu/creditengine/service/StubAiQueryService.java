package com.tanzu.creditengine.service;

import com.tanzu.creditengine.ai.CreditTools;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local profile: a deterministic stand-in for the GenAI service so the assistant panel
 * works during frontend inspection without a {@code credit-chat} binding. It does light
 * intent matching and calls the same {@link CreditTools} the real model would, so the
 * table renders identically.
 */
@Service
@Profile("local")
public class StubAiQueryService implements AiQueryService {

    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*min");
    private static final Pattern TOP_N = Pattern.compile("(?:top|last|first)\\s*(\\d+)");

    private final CreditTools tools;

    public StubAiQueryService(CreditTools tools) {
        this.tools = tools;
    }

    @Override
    public AiAnswer answer(String prompt) {
        String p = prompt == null ? "" : prompt.toLowerCase();
        tools.begin();
        String message;

        int limit = firstInt(TOP_N, p, 10);

        if (p.contains("min")) {
            int minutes = firstInt(MINUTES, p, 15);
            message = tools.scoresInLastMinutes(minutes, limit);
        } else if (p.contains("high risk") || p.contains("very high")) {
            message = tools.filterByRiskLevel(p.contains("very high") ? "VERY_HIGH_RISK" : "HIGH_RISK");
        } else if (p.contains("low risk")) {
            message = tools.filterByRiskLevel("LOW_RISK");
        } else if (p.contains("medium risk")) {
            message = tools.filterByRiskLevel("MEDIUM_RISK");
        } else {
            message = tools.topScores(limit);
        }

        List<Map<String, Object>> rows = tools.drain();
        return new AiAnswer("[local stub] " + message, rows);
    }

    private static int firstInt(Pattern pattern, String text, int fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }
}
