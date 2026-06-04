package com.tanzu.creditengine.ai;

import com.tanzu.creditengine.cache.CachedScore;
import com.tanzu.creditengine.service.CreditScoreCalculator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed tools exposed to the LLM. The model chooses which tool to call and with what
 * arguments — it never writes SQL, so there is no injection surface. Each tool reads
 * from the bounded score cache (fast, no keyspace scans) and records the rows it returns
 * in a thread-local buffer so the controller can render them as a table.
 */
@Component
public class CreditTools {

    private final CreditScoreCalculator calculator;
    private final ThreadLocal<List<Map<String, Object>>> buffer = ThreadLocal.withInitial(ArrayList::new);

    public CreditTools(CreditScoreCalculator calculator) {
        this.calculator = calculator;
    }

    /** Start collecting rows for one request. */
    public void begin() {
        buffer.get().clear();
    }

    /** Take the rows collected during the current request and reset. */
    public List<Map<String, Object>> drain() {
        List<Map<String, Object>> rows = new ArrayList<>(buffer.get());
        buffer.remove();
        return rows;
    }

    @Tool(description = "Get the top N most recently calculated credit scores, newest first.")
    public String topScores(@ToolParam(description = "How many scores to return, e.g. 10") int limit) {
        List<CachedScore> scores = calculator.topRecent(clamp(limit));
        record(scores);
        return summarize(scores, "most recent scores");
    }

    @Tool(description = "Get credit scores calculated within the last N minutes, newest first.")
    public String scoresInLastMinutes(
            @ToolParam(description = "Look-back window in minutes, e.g. 15") int minutes,
            @ToolParam(description = "Maximum number of scores to return, e.g. 10") int limit) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, minutes));
        List<CachedScore> scores = calculator.topRecent(100).stream()
                .filter(s -> s.getCalculatedAt() != null && s.getCalculatedAt().isAfter(cutoff))
                .limit(clamp(limit))
                .toList();
        record(scores);
        return summarize(scores, "scores in the last " + minutes + " minutes");
    }

    @Tool(description = "Get recent credit scores filtered by risk level: LOW_RISK, MEDIUM_RISK, HIGH_RISK or VERY_HIGH_RISK.")
    public String filterByRiskLevel(@ToolParam(description = "Risk level to filter by") String riskLevel) {
        String wanted = riskLevel == null ? "" : riskLevel.trim().toUpperCase();
        List<CachedScore> scores = calculator.topRecent(100).stream()
                .filter(s -> s.getRiskLevel() != null && s.getRiskLevel().equalsIgnoreCase(wanted))
                .toList();
        record(scores);
        return summarize(scores, wanted + " scores");
    }

    @Tool(description = "Look up the calculated credit score for a single SSN.")
    public String scoreForSsn(@ToolParam(description = "The SSN to look up") String ssn) {
        return calculator.getScore(ssn)
                .map(s -> {
                    record(List.of(s));
                    return "Score for " + mask(ssn) + " is " + s.getCalculatedScore()
                            + " (" + s.getRiskLevel() + ").";
                })
                .orElse("No calculated score found for that SSN.");
    }

    private void record(List<CachedScore> scores) {
        List<Map<String, Object>> rows = buffer.get();
        for (CachedScore s : scores) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("SSN", mask(s.getSsn()));
            row.put("Full Name", s.getFullName() != null ? s.getFullName() : "--");
            row.put("Score", s.getCalculatedScore());
            row.put("Risk Level", s.getRiskLevel());
            rows.add(row);
        }
    }

    private static String summarize(List<CachedScore> scores, String label) {
        if (scores.isEmpty()) {
            return "No " + label + " were found.";
        }
        return "Found " + scores.size() + " " + label + ".";
    }

    private static int clamp(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    private static String mask(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "***";
        }
        return "***-**-" + ssn.substring(ssn.length() - 4);
    }
}
