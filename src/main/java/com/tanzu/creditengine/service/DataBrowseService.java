package com.tanzu.creditengine.service;

import com.tanzu.creditengine.entity.CreditHistory;
import com.tanzu.creditengine.entity.CreditScore;
import com.tanzu.creditengine.entity.Customer;
import com.tanzu.creditengine.repository.CreditHistoryRepository;
import com.tanzu.creditengine.repository.CreditScoreRepository;
import com.tanzu.creditengine.repository.CriminalRecordRepository;
import com.tanzu.creditengine.repository.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only browse of the PostgreSQL source-of-truth tables, for the admin portal.
 * Lets an operator see the seeded customer data (which isn't visible in the dashboard's
 * "latest scores" table until a score is actually calculated).
 */
@Service
public class DataBrowseService {

    /** One row of the admin data browser. */
    public record CustomerView(
            String ssn,
            String fullName,
            String country,
            Integer creditHistoryScore,
            Integer delinquencies,
            long criminalRecords,
            Integer calculatedScore,
            String riskLevel) {
    }

    private final CustomerRepository customers;
    private final CreditHistoryRepository creditHistory;
    private final CriminalRecordRepository criminalRecords;
    private final CreditScoreRepository creditScores;

    public DataBrowseService(CustomerRepository customers,
            CreditHistoryRepository creditHistory,
            CriminalRecordRepository criminalRecords,
            CreditScoreRepository creditScores) {
        this.customers = customers;
        this.creditHistory = creditHistory;
        this.criminalRecords = criminalRecords;
        this.creditScores = creditScores;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("customers", customers.count());
        m.put("creditHistory", creditHistory.count());
        m.put("criminalRecords", criminalRecords.count());
        m.put("creditScores", creditScores.count());
        return m;
    }

    @Transactional(readOnly = true)
    public List<CustomerView> recentCustomers(int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        return customers.findAll(PageRequest.of(0, capped)).stream()
                .map(this::toView)
                .toList();
    }

    private CustomerView toView(Customer c) {
        CreditHistory latest = creditHistory
                .findLatestBySsn(c.getSsn(), PageRequest.of(0, 1)).stream()
                .findFirst().orElse(null);
        long criminal = criminalRecords.countBySsn(c.getSsn());
        CreditScore score = creditScores.findById(c.getSsn()).orElse(null);

        return new CustomerView(
                maskSsn(c.getSsn()),
                c.getFullName(),
                c.getCountry(),
                latest != null ? latest.getCreditHistoryScore() : null,
                latest != null ? latest.getDelinquencies() : null,
                criminal,
                score != null ? score.getCalculatedScore() : null,
                score != null ? score.getRiskLevel() : null);
    }

    private static String maskSsn(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "***";
        }
        return "***-**-" + ssn.substring(ssn.length() - 4);
    }
}
