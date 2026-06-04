package com.tanzu.creditengine.service;

import com.tanzu.creditengine.entity.AppSettings;
import com.tanzu.creditengine.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the singleton {@link AppSettings} row that drives runtime
 * behaviour configured from the admin portal (cache eviction TTL, top-N size, UI
 * refresh interval, AI toggle).
 */
@Service
public class SettingsService {

    private final AppSettingsRepository repository;

    public SettingsService(AppSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AppSettings current() {
        return repository.findById(AppSettings.SINGLETON_ID).orElseGet(AppSettings::new);
    }

    public long cacheTtlSeconds() {
        return current().getCacheTtlSeconds();
    }

    public int topNSize() {
        return current().getTopNSize();
    }

    @Transactional
    public AppSettings update(long cacheTtlSeconds, int topNSize, long refreshIntervalMs, boolean aiEnabled) {
        AppSettings settings = repository.findById(AppSettings.SINGLETON_ID).orElseGet(AppSettings::new);
        settings.setId(AppSettings.SINGLETON_ID);
        if (cacheTtlSeconds > 0) {
            settings.setCacheTtlSeconds(cacheTtlSeconds);
        }
        if (topNSize > 0) {
            settings.setTopNSize(topNSize);
        }
        if (refreshIntervalMs > 0) {
            settings.setRefreshIntervalMs(refreshIntervalMs);
        }
        settings.setAiEnabled(aiEnabled);
        return repository.save(settings);
    }
}
