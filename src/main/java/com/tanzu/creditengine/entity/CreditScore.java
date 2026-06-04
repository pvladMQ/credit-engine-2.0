package com.tanzu.creditengine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * The calculated credit score, persisted in PostgreSQL as the authoritative result.
 * Valkey only ever holds a cache-aside copy of this row.
 */
@Entity
@Table(name = "credit_scores")
public class CreditScore {

    @Id
    @Column(name = "ssn", length = 11)
    private String ssn;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "calculated_score")
    private Integer calculatedScore;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    public CreditScore() {
    }

    public CreditScore(String ssn, String fullName, Integer calculatedScore, String riskLevel,
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
