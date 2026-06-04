package com.tanzu.creditengine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * A customer — the root identity record. The source of truth for who a person is.
 * Credit history and criminal records hang off the SSN.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(name = "ssn", length = 11)
    private String ssn;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "country", length = 40)
    private String country;

    public Customer() {
    }

    public Customer(String ssn, String fullName, LocalDate dateOfBirth, String country) {
        this.ssn = ssn;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.country = country;
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

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
