package com.tanzu.creditengine.service;

import com.tanzu.creditengine.entity.CreditHistory;
import com.tanzu.creditengine.entity.CreditScore;
import com.tanzu.creditengine.entity.Customer;
import com.tanzu.creditengine.messaging.ScoreRequest;
import com.tanzu.creditengine.repository.CreditHistoryRepository;
import com.tanzu.creditengine.repository.CreditScoreRepository;
import com.tanzu.creditengine.repository.CriminalRecordRepository;
import com.tanzu.creditengine.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Owns the <em>database</em> side of scoring. Every method here is transactional and
 * touches PostgreSQL ONLY — no Valkey, no RabbitMQ. This is the key fix for the Tanzu
 * Hub distributed-transaction alert: the transaction scope is confined to the single
 * resource manager (the database service) that owns it, so the app scales horizontally
 * without cross-resource transaction coordination.
 */
@Service
public class ScorePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ScorePersistenceService.class);

    private final CustomerRepository customers;
    private final CreditHistoryRepository creditHistory;
    private final CriminalRecordRepository criminalRecords;
    private final CreditScoreRepository creditScores;

    public ScorePersistenceService(CustomerRepository customers,
            CreditHistoryRepository creditHistory,
            CriminalRecordRepository criminalRecords,
            CreditScoreRepository creditScores) {
        this.customers = customers;
        this.creditHistory = creditHistory;
        this.criminalRecords = criminalRecords;
        this.creditScores = creditScores;
    }

    /**
     * Performs the real multi-table join, calculates the score and persists it. Returns a
     * plain {@link ScoreResult} so the caller can update the cache AFTER this transaction
     * commits. Runs entirely within a single PostgreSQL transaction.
     */
    @Transactional
    public ScoreResult scoreAndPersist(ScoreRequest request) {
        String ssn = request.getSsn();

        Customer customer = customers.findById(ssn)
                .orElseGet(() -> customers.save(new Customer(ssn, request.getFullName(),
                        LocalDate.now().minusYears(30), "USA")));

        CreditHistory latest = creditHistory
                .findLatestBySsn(ssn, PageRequest.of(0, 1)).stream()
                .findFirst()
                .orElse(null);

        // First time we see this SSN with no history on file: synthesize a deterministic
        // credit-history record from the SSN so scores vary realistically across applicants
        // (deterministic hash — no RNG in the request path).
        if (latest == null) {
            latest = creditHistory.save(syntheticHistory(ssn));
        }

        long highSeverity = criminalRecords.countBySsnAndSeverity(ssn, "HIGH");
        long totalRecords = criminalRecords.countBySsn(ssn);

        int score = calculateScore(latest, highSeverity, totalRecords);
        String riskLevel = determineRiskLevel(score);
        LocalDateTime now = LocalDateTime.now();

        CreditScore persisted = new CreditScore(ssn, customer.getFullName(), score, riskLevel, now);
        creditScores.save(persisted);

        log.debug("Persisted score for {} -> {} ({})", ssn, score, riskLevel);
        return new ScoreResult(ssn, customer.getFullName(), score, riskLevel, now);
    }

    /**
     * Scoring algorithm (1-100) derived from the joined source tables:
     * credit-history signals plus criminal-record severity.
     */
    private int calculateScore(CreditHistory history, long highSeverityRecords, long totalRecords) {
        int base = 50;

        if (history != null && history.getCreditHistoryScore() != null) {
            int h = history.getCreditHistoryScore();
            if (h >= 750) {
                base += 30;
            } else if (h >= 650) {
                base += 20;
            } else if (h >= 550) {
                base += 10;
            } else {
                base -= 10;
            }
            if (history.getDelinquencies() != null) {
                base -= Math.min(15, history.getDelinquencies() * 3);
            }
        }

        // Criminal records reduce the score; high-severity offenses weigh more.
        base -= (int) Math.min(25, highSeverityRecords * 15 + (totalRecords - highSeverityRecords) * 5);

        return Math.max(1, Math.min(100, base));
    }

    /** Deterministic synthetic history derived from the SSN — same SSN always yields the same profile. */
    private CreditHistory syntheticHistory(String ssn) {
        int h = Math.abs(ssn.hashCode());
        int historyScore = 300 + (h % 551);          // 300-850
        int openAccounts = 1 + (h % 8);              // 1-8
        int delinquencies = (h / 7) % 5;             // 0-4
        java.math.BigDecimal totalDebt = java.math.BigDecimal.valueOf(1000 + (h % 90000));
        return new CreditHistory(ssn, historyScore, openAccounts, delinquencies, totalDebt, LocalDate.now());
    }

    private String determineRiskLevel(int score) {
        if (score >= 80) {
            return "LOW_RISK";
        } else if (score >= 60) {
            return "MEDIUM_RISK";
        } else if (score >= 40) {
            return "HIGH_RISK";
        } else {
            return "VERY_HIGH_RISK";
        }
    }

    @Transactional(readOnly = true)
    public List<CreditScore> recentScores(int n) {
        return creditScores.findAllByOrderByCalculatedAtDesc(PageRequest.of(0, n));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<CreditScore> findScore(String ssn) {
        return creditScores.findById(ssn);
    }
}
