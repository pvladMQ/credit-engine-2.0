package com.tanzu.creditengine.controller;

import com.tanzu.creditengine.entity.AppSettings;
import com.tanzu.creditengine.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin portal API: read and update runtime configuration — Valkey cache eviction TTL,
 * top-N size, UI auto-refresh interval and the AI toggle. The admin page is served as a
 * static file at {@code /admin.html}.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final SettingsService settings;

    public AdminController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> get() {
        return ResponseEntity.ok(toMap(settings.current()));
    }

    @PostMapping("/settings")
    public ResponseEntity<Map<String, Object>> update(@RequestBody Map<String, Object> payload) {
        long ttl = asLong(payload.get("cacheTtlSeconds"), settings.current().getCacheTtlSeconds());
        int topN = (int) asLong(payload.get("topNSize"), settings.current().getTopNSize());
        long refresh = asLong(payload.get("refreshIntervalMs"), settings.current().getRefreshIntervalMs());
        boolean ai = payload.get("aiEnabled") == null
                ? settings.current().isAiEnabled()
                : Boolean.parseBoolean(String.valueOf(payload.get("aiEnabled")));

        AppSettings updated = settings.update(ttl, topN, refresh, ai);
        log.info("Admin updated settings: ttl={}s topN={} refresh={}ms ai={}",
                updated.getCacheTtlSeconds(), updated.getTopNSize(),
                updated.getRefreshIntervalMs(), updated.isAiEnabled());
        return ResponseEntity.ok(toMap(updated));
    }

    private Map<String, Object> toMap(AppSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cacheTtlSeconds", s.getCacheTtlSeconds());
        m.put("topNSize", s.getTopNSize());
        m.put("refreshIntervalMs", s.getRefreshIntervalMs());
        m.put("aiEnabled", s.isAiEnabled());
        return m;
    }

    private static long asLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
