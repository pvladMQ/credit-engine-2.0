package com.tanzu.creditengine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * A criminal record for a customer (another source table feeding the score).
 * Severity downgrades the calculated credit score.
 */
@Entity
@Table(name = "criminal_records", indexes = @Index(name = "idx_criminal_records_ssn", columnList = "ssn"))
public class CriminalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ssn", nullable = false, length = 11)
    private String ssn;

    @Column(name = "offense", length = 120)
    private String offense;

    /** LOW, MEDIUM or HIGH. */
    @Column(name = "severity", length = 10)
    private String severity;

    @Column(name = "record_date")
    private LocalDate recordDate;

    public CriminalRecord() {
    }

    public CriminalRecord(String ssn, String offense, String severity, LocalDate recordDate) {
        this.ssn = ssn;
        this.offense = offense;
        this.severity = severity;
        this.recordDate = recordDate;
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

    public String getOffense() {
        return offense;
    }

    public void setOffense(String offense) {
        this.offense = offense;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }
}
