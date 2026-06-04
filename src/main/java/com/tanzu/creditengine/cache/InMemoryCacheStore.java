package com.tanzu.creditengine.cache;

import com.tanzu.creditengine.service.SettingsService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for Valkey used by the {@code local} profile so the UI can be
 * inspected without any external services. Mirrors the Valkey semantics: keyed access,
 * a bounded recency view and TTL-based eviction (lazy).
 */
@Component
@Profile("local")
public class InMemoryCacheStore implements CreditScoreCacheStore {

    private record Entry(CachedScore score, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final SettingsService settings;

    public InMemoryCacheStore(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public void put(CachedScore score) {
        Instant expiry = Instant.now().plusSeconds(Math.max(1, settings.cacheTtlSeconds()));
        store.put(score.getSsn(), new Entry(score, expiry));
    }

    @Override
    public Optional<CachedScore> get(String ssn) {
        Entry e = store.get(ssn);
        if (e == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(e.expiresAt())) {
            store.remove(ssn);
            return Optional.empty();
        }
        return Optional.of(e.score());
    }

    @Override
    public List<CachedScore> topRecent(int n) {
        Instant now = Instant.now();
        List<CachedScore> live = new ArrayList<>();
        store.values().forEach(e -> {
            if (now.isAfter(e.expiresAt())) {
                store.remove(e.score().getSsn());
            } else {
                live.add(e.score());
            }
        });
        live.sort(Comparator.comparing(CachedScore::getCalculatedAt).reversed());
        return live.size() > n ? new ArrayList<>(live.subList(0, n)) : live;
    }

    @Override
    public long size() {
        return store.size();
    }
}
