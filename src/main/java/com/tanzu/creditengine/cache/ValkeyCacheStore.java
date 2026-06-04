package com.tanzu.creditengine.cache;

import com.tanzu.creditengine.service.SettingsService;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Valkey-backed cache (cloud profile).
 *
 * <p>Design choices that keep CPU cost bounded:
 * <ul>
 *   <li>Each score is a single string key {@code score:{ssn}} with a TTL — O(1) GET/SET,
 *       no secondary indexes, no {@code KEYS} scan.</li>
 *   <li>"Top recent" is a single capped sorted set {@code scores:recent} (score = epoch
 *       millis). Reads are {@code ZREVRANGE} (O(log N + n)); the set is trimmed on write.</li>
 * </ul>
 * This replaces the original {@code @RedisHash} + {@code findAll()} approach that issued a
 * full-keyspace scan on every 5-second dashboard refresh.
 */
@Component
@Profile("cloud")
public class ValkeyCacheStore implements CreditScoreCacheStore {

    private static final String KEY_PREFIX = "score:";
    private static final String RECENT_ZSET = "scores:recent";
    private static final int RECENT_HARD_CAP = 100;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SettingsService settings;

    public ValkeyCacheStore(RedisTemplate<String, Object> redisTemplate, SettingsService settings) {
        this.redisTemplate = redisTemplate;
        this.settings = settings;
    }

    private static String key(String ssn) {
        return KEY_PREFIX + ssn;
    }

    @Override
    public void put(CachedScore score) {
        Duration ttl = Duration.ofSeconds(Math.max(1, settings.cacheTtlSeconds()));
        redisTemplate.opsForValue().set(key(score.getSsn()), score, ttl);

        long epochMillis = score.getCalculatedAt()
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        ZSetOperations<String, Object> zset = redisTemplate.opsForZSet();
        zset.add(RECENT_ZSET, score.getSsn(), epochMillis);
        // Trim to a hard cap so the recency index never grows unbounded.
        Long count = zset.zCard(RECENT_ZSET);
        if (count != null && count > RECENT_HARD_CAP) {
            zset.removeRange(RECENT_ZSET, 0, count - RECENT_HARD_CAP - 1);
        }
    }

    @Override
    public Optional<CachedScore> get(String ssn) {
        Object value = redisTemplate.opsForValue().get(key(ssn));
        return Optional.ofNullable((CachedScore) value);
    }

    @Override
    public List<CachedScore> topRecent(int n) {
        Set<Object> ssns = redisTemplate.opsForZSet().reverseRange(RECENT_ZSET, 0, n - 1L);
        List<CachedScore> result = new ArrayList<>();
        if (ssns == null) {
            return result;
        }
        for (Object ssn : ssns) {
            Object value = redisTemplate.opsForValue().get(key((String) ssn));
            if (value instanceof CachedScore cs) {
                result.add(cs);
            } else {
                // Entry expired by TTL — drop it from the recency index lazily.
                redisTemplate.opsForZSet().remove(RECENT_ZSET, ssn);
            }
        }
        return result;
    }

    @Override
    public long size() {
        Long count = redisTemplate.opsForZSet().zCard(RECENT_ZSET);
        return count == null ? 0 : count;
    }
}
