package com.tanzu.creditengine.controller;

import com.tanzu.creditengine.service.AiAnswer;
import com.tanzu.creditengine.service.AiQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Natural-language querying endpoint backed by Spring AI tool-calling (cloud) or a
 * deterministic stub (local). Returns either a list of structured rows (rendered as a
 * table by the UI) or a {@code {message}} object.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiQueryService aiQueryService;

    public AiController(AiQueryService aiQueryService) {
        this.aiQueryService = aiQueryService;
    }

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody Map<String, String> payload) {
        String prompt = payload.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Prompt cannot be empty"));
        }
        log.info("AI query: {}", prompt);

        AiAnswer answer = aiQueryService.answer(prompt);
        if (answer.rows().isEmpty()) {
            return ResponseEntity.ok(Map.of("message", answer.message()));
        }
        return ResponseEntity.ok(Map.of(
                "message", answer.message(),
                "rows", answer.rows()));
    }
}
