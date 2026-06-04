package com.tanzu.creditengine.cache;

import java.util.List;
import java.util.Optional;

/**
 * Cache-aside abstraction over the score cache. The cloud implementation is backed by
 * Valkey; the local implementation is an in-memory map. All writes are bounded and
 * keyed — no full-keyspace scans — and carry a TTL sourced from admin settings.
 */
public interface CreditScoreCacheStore {

    /** Write-through a freshly calculated score (applies the configured eviction TTL). */
    void put(CachedScore score);

    /** Look up a single score by SSN. */
    Optional<CachedScore> get(String ssn);

    /** The {@code n} most-recently calculated scores, newest first. Bounded, O(log N). */
    List<CachedScore> topRecent(int n);

    /** Number of entries currently cached. */
    long size();
}
