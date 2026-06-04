package com.tanzu.creditengine.messaging;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * Inbound scoring request. Published to RabbitMQ (cloud) or handed off in-process
 * (local) and consumed by the scoring pipeline.
 */
public class ScoreRequest implements Serializable {

    private static final long serialVersionUID = 2L;

    @NotBlank
    private String ssn;

    @NotBlank
    private String fullName;

    private Integer requestedCreditLimit;
    private String applicationReason;

    public ScoreRequest() {
    }

    public ScoreRequest(String ssn, String fullName, Integer requestedCreditLimit, String applicationReason) {
        this.ssn = ssn;
        this.fullName = fullName;
        this.requestedCreditLimit = requestedCreditLimit;
        this.applicationReason = applicationReason;
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

    public Integer getRequestedCreditLimit() {
        return requestedCreditLimit;
    }

    public void setRequestedCreditLimit(Integer requestedCreditLimit) {
        this.requestedCreditLimit = requestedCreditLimit;
    }

    public String getApplicationReason() {
        return applicationReason;
    }

    public void setApplicationReason(String applicationReason) {
        this.applicationReason = applicationReason;
    }

    @Override
    public String toString() {
        return "ScoreRequest{ssn='" + ssn + "', fullName='" + fullName + "'}";
    }
}
