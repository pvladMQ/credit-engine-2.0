package com.tanzu.creditengine.service;

import com.tanzu.creditengine.cache.CachedScore;
import com.tanzu.creditengine.cache.CreditScoreCacheStore;
import com.tanzu.creditengine.entity.CreditScore;
import com.tanzu.creditengine.messaging.ScoreRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates a scoring request. This class is intentionally <strong>not</strong>
 * {@code @Transactional}: it calls the transactional persistence service (PostgreSQL
 * only), and once that transaction has committed it performs the cache-aside write to
 * Valkey. A cache failure can therefore never roll back the database, and the database
 * transaction never spans a second resource manager.
 */
@Service
public class CreditScoreCalculator {

    private static final Logger log = LoggerFactory.getLogger(CreditScoreCalculator.class);

    private final ScorePersistenceService persistence;
    private final CreditScoreCacheStore cache;
    private final MetricsService metrics;

    public CreditScoreCalculator(ScorePersistenceService persistence,
            CreditScoreCacheStore cache,
            MetricsService metrics) {
        this.persistence = persistence;
        this.cache = cache;
        this.metrics = metrics;
    }

    public ScoreResult process(ScoreRequest request) {
        // 1) Database transaction (Postgres only) — commits before we touch the cache.
        long pgStart = System.currentTimeMillis();
        ScoreResult result = persistence.scoreAndPersist(request);
        metrics.recordPostgresQuery(System.currentTimeMillis() - pgStart);

        // 2) Cache-aside write, strictly AFTER the transaction has committed.
        CachedScore cached = new CachedScore(result.ssn(), result.fullName(),
                result.calculatedScore(), result.riskLevel(), result.calculatedAt());
        long cacheStart = System.currentTimeMillis();
        try {
            cache.put(cached);
            metrics.recordValkeyQuery(System.currentTimeMillis() - cacheStart);
        } catch (RuntimeException ex) {
            // Cache is best-effort; the authoritative score is already safely in Postgres.
            log.warn("Cache write failed for {} (score is persisted in Postgres): {}",
                    result.ssn(), ex.getMessage());
        }

        metrics.recordMessageProcessed();
        metrics.logEvent("Scored & cached " + mask(result.ssn()) + " -> " + result.calculatedScore());
        return result;
    }

    /** Cache-first read with a PostgreSQL fallback on miss. */
    public Optional<CachedScore> getScore(String ssn) {
        long start = System.currentTimeMillis();
        Optional<CachedScore> hit = cache.get(ssn);
        metrics.recordValkeyQuery(System.currentTimeMillis() - start);
        if (hit.isPresent()) {
            metrics.recordCacheHit();
            return hit;
        }
        metrics.recordCacheMiss();
        return persistence.findScore(ssn).map(this::toCached);
    }

    public List<CachedScore> topRecent(int n) {
        return cache.topRecent(n);
    }

    private CachedScore toCached(CreditScore s) {
        return new CachedScore(s.getSsn(), s.getFullName(), s.getCalculatedScore(),
                s.getRiskLevel(), s.getCalculatedAt());
    }

    private static String mask(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "***";
        }
        return "***-**-" + ssn.substring(ssn.length() - 4);
    }
}
