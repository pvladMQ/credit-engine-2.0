package com.tanzu.creditengine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row runtime configuration edited from the admin portal and persisted in
 * PostgreSQL so it survives restarts. Always stored under {@code id = 1}.
 */
@Entity
@Table(name = "app_settings")
public class AppSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id")
    private Long id = SINGLETON_ID;

    /** Valkey cache eviction TTL, in seconds, applied to every cached score. */
    @Column(name = "cache_ttl_seconds")
    private long cacheTtlSeconds = 300;

    /** How many recent scores the "top" table / cache structure retains. */
    @Column(name = "top_n_size")
    private int topNSize = 10;

    /** UI auto-refresh interval (ms) handed to the frontend. */
    @Column(name = "refresh_interval_ms")
    private long refreshIntervalMs = 5000;

    /** Whether the GenAI assistant is enabled. */
    @Column(name = "ai_enabled")
    private boolean aiEnabled = true;

    public AppSettings() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public int getTopNSize() {
        return topNSize;
    }

    public void setTopNSize(int topNSize) {
        this.topNSize = topNSize;
    }

    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }

    public void setRefreshIntervalMs(long refreshIntervalMs) {
        this.refreshIntervalMs = refreshIntervalMs;
    }

    public boolean isAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }
}
