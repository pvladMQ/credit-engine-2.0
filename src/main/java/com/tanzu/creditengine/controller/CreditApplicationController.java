package com.tanzu.creditengine.controller;

import com.tanzu.creditengine.cache.CachedScore;
import com.tanzu.creditengine.entity.CreditScore;
import com.tanzu.creditengine.messaging.ScoreRequest;
import com.tanzu.creditengine.messaging.ScoreRequestPublisher;
import com.tanzu.creditengine.service.CreditScoreCalculator;
import com.tanzu.creditengine.service.MetricsService;
import com.tanzu.creditengine.service.ScorePersistenceService;
import com.tanzu.creditengine.service.SettingsService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Operator-facing API: submit scoring requests, read scores from the cache, view the
 * bounded "top recent" table, header metrics and the Postgres-vs-Valkey latency test.
 */
@RestController
@RequestMapping("/api")
public class CreditApplicationController {

    private static final Logger log = LoggerFactory.getLogger(CreditApplicationController.class);

    private final ScoreRequestPublisher publisher;
    private final CreditScoreCalculator calculator;
    private final ScorePersistenceService persistence;
    private final MetricsService metrics;
    private final SettingsService settings;

    public CreditApplicationController(ScoreRequestPublisher publisher,
            CreditScoreCalculator calculator,
            ScorePersistenceService persistence,
            MetricsService metrics,
            SettingsService settings) {
        this.publisher = publisher;
        this.calculator = calculator;
        this.persistence = persistence;
        this.metrics = metrics;
        this.settings = settings;
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody ScoreRequest request) {
        publisher.publish(request);
        metrics.recordApplication();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "accepted");
        body.put("message", "Scoring request submitted for processing");
        body.put("ssn", request.getSsn());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/score/{ssn}")
    public ResponseEntity<Map<String, Object>> getScore(@PathVariable String ssn) {
        long start = System.currentTimeMillis();
        Optional<CachedScore> score = calculator.getScore(ssn);
        long queryMs = System.currentTimeMillis() - start;

        if (score.isEmpty()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "not_found");
            body.put("message", "No credit score found for SSN: " + ssn);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        CachedScore s = score.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "success");
        body.put("ssn", s.getSsn());
        body.put("fullName", s.getFullName());
        body.put("calculatedScore", s.getCalculatedScore());
        body.put("riskLevel", s.getRiskLevel());
        body.put("calculatedAt", s.getCalculatedAt());
        body.put("queryTimeMs", queryMs);
        body.put("source", "Valkey cache (with Postgres fallback)");
        return ResponseEntity.ok(body);
    }

    /** Bounded "top N recent" — O(log N) cache read, never a full keyspace scan. */
    @GetMapping("/scores/top")
    public ResponseEntity<Map<String, Object>> topScores() {
        long start = System.currentTimeMillis();
        List<CachedScore> scores = calculator.topRecent(settings.topNSize());
        long queryMs = System.currentTimeMillis() - start;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "success");
        body.put("count", scores.size());
        body.put("scores", scores);
        body.put("queryTimeMs", queryMs);
        body.put("source", "Valkey cache");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalApplications", metrics.getTotalApplications());
        body.put("messagesProcessed", metrics.getMessagesProcessed());
        body.put("avgPostgresTimeMs", round(metrics.getAveragePostgresTimeMs()));
        body.put("avgValkeyTimeMs", round(metrics.getAverageValkeyTimeMs()));
        body.put("cacheHits", metrics.getCacheHits());
        body.put("cacheMisses", metrics.getCacheMisses());
        body.put("cacheHitRate", round(metrics.getCacheHitRate()));
        body.put("speedupRatio", round(metrics.getSpeedupRatio()));
        body.put("recentEvents", metrics.getRecentEvents());
        body.put("refreshIntervalMs", settings.current().getRefreshIntervalMs());
        return ResponseEntity.ok(body);
    }

    /** One-shot Postgres-vs-Valkey latency comparison for the same SSN. */
    @GetMapping("/latency-test/{ssn}")
    public ResponseEntity<Map<String, Object>> latencyTest(@PathVariable String ssn) {
        long pgStart = System.currentTimeMillis();
        Optional<CreditScore> pg = persistence.findScore(ssn);
        long pgMs = System.currentTimeMillis() - pgStart;
        metrics.recordPostgresQuery(pgMs);

        long vkStart = System.currentTimeMillis();
        Optional<CachedScore> vk = calculator.getScore(ssn);
        long vkMs = System.currentTimeMillis() - vkStart;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ssn", ssn);
        body.put("postgresTimeMs", pgMs);
        body.put("valkeyTimeMs", vkMs);
        body.put("speedup", (pgMs > 0 && vkMs > 0) ? Math.round((double) pgMs / vkMs * 10.0) / 10.0 : 0);
        body.put("postgresFound", pg.isPresent());
        body.put("valkeyFound", vk.isPresent());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "Global Credit Scoring Engine");

        String vcap = System.getenv("VCAP_SERVICES");
        boolean onCf = vcap != null && !vcap.isBlank() && !vcap.equals("{}");
        body.put("cloudFoundry", onCf);

        List<Map<String, Object>> services = new ArrayList<>();
        for (String[] svc : new String[][] {
                { "credit-db", "PostgreSQL" }, { "credit-cache", "Valkey" },
                { "credit-msg", "RabbitMQ" }, { "credit-chat", "GenAI" } }) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", svc[0]);
            s.put("type", svc[1]);
            s.put("status", !onCf ? "local" : (vcap.contains(svc[0]) ? "bound" : "missing"));
            services.add(s);
        }
        body.put("boundServices", services);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "Global Credit Scoring Engine"));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
