package com.tanzu.creditengine.cache;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Plain serializable view of a calculated score held in Valkey (cache-aside copy of
 * the authoritative {@code credit_scores} row). Deliberately NOT a {@code @RedisHash}
 * entity — we access it through explicit keys to avoid Redis {@code KEYS}/SCAN storms.
 */
public class CachedScore implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ssn;
    private String fullName;
    private Integer calculatedScore;
    private String riskLevel;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime calculatedAt;

    public CachedScore() {
    }

    public CachedScore(String ssn, String fullName, Integer calculatedScore, String riskLevel,
            LocalDateTime calculatedAt) {
        this.ssn = ssn;
        this.fullName = fullName;
        this.calculatedScore = calculatedScore;
        this.riskLevel = riskLevel;
        this.calculatedAt = calculatedAt;
    }

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Integer getCalculatedScore() {
        return calculatedScore;
    }

    public void setCalculatedScore(Integer calculatedScore) {
        this.calculatedScore = calculatedScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(LocalDateTime calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}
