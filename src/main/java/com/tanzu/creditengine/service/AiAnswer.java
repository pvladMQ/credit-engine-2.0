package com.tanzu.creditengine.service;

import java.util.List;
import java.util.Map;

/**
 * Result of a natural-language query: a human-readable {@code message} from the model
 * plus any structured {@code rows} the invoked tools produced (rendered as a table).
 */
public record AiAnswer(String message, List<Map<String, Object>> rows) {

    public static AiAnswer message(String message) {
        return new AiAnswer(message, List.of());
    }
}
