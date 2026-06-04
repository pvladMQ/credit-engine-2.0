package com.tanzu.creditengine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A credit-history observation for a customer (one of several "source" tables the
 * score is joined from). The most recent record per SSN feeds the calculation.
 */
@Entity
@Table(name = "credit_history", indexes = @Index(name = "idx_credit_history_ssn", columnList = "ssn"))
public class CreditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ssn", nullable = false, length = 11)
    private String ssn;

    @Column(name = "credit_history_score")
    private Integer creditHistoryScore;

    @Column(name = "open_accounts")
    private Integer openAccounts;

    @Column(name = "delinquencies")
    private Integer delinquencies;

    @Column(name = "total_debt", precision = 12, scale = 2)
    private BigDecimal totalDebt;

    @Column(name = "recorded_at")
    private LocalDate recordedAt;

    public CreditHistory() {
    }

    public CreditHistory(String ssn, Integer creditHistoryScore, Integer openAccounts,
            Integer delinquencies, BigDecimal totalDebt, LocalDate recordedAt) {
        this.ssn = ssn;
        this.creditHistoryScore = creditHistoryScore;
        this.openAccounts = openAccounts;
        this.delinquencies = delinquencies;
        this.totalDebt = totalDebt;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public Integer getCreditHistoryScore() {
        return creditHistoryScore;
    }

    public void setCreditHistoryScore(Integer creditHistoryScore) {
        this.creditHistoryScore = creditHistoryScore;
    }

    public Integer getOpenAccounts() {
        return openAccounts;
    }

    public void setOpenAccounts(Integer openAccounts) {
        this.openAccounts = openAccounts;
    }

    public Integer getDelinquencies() {
        return delinquencies;
    }

    public void setDelinquencies(Integer delinquencies) {
        this.delinquencies = delinquencies;
    }

    public BigDecimal getTotalDebt() {
        return totalDebt;
    }

    public void setTotalDebt(BigDecimal totalDebt) {
        this.totalDebt = totalDebt;
    }

    public LocalDate getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDate recordedAt) {
        this.recordedAt = recordedAt;
    }
}
