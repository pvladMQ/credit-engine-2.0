package com.tanzu.creditengine.service;

import java.time.LocalDateTime;

/**
 * Immutable outcome of a scoring calculation, returned from the transactional
 * persistence layer to the (non-transactional) orchestrator so the cache write can
 * happen strictly after the database transaction has committed.
 */
public record ScoreResult(
        String ssn,
        String fullName,
        int calculatedScore,
        String riskLevel,
        LocalDateTime calculatedAt) {
}
