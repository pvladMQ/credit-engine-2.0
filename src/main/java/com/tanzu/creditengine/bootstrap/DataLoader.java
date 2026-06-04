package com.tanzu.creditengine.bootstrap;

import com.tanzu.creditengine.entity.AppSettings;
import com.tanzu.creditengine.entity.CreditHistory;
import com.tanzu.creditengine.entity.Customer;
import com.tanzu.creditengine.entity.CriminalRecord;
import com.tanzu.creditengine.repository.AppSettingsRepository;
import com.tanzu.creditengine.repository.CreditHistoryRepository;
import com.tanzu.creditengine.repository.CriminalRecordRepository;
import com.tanzu.creditengine.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds the normalized source-of-truth tables (customers, credit_history,
 * criminal_records) with coherent, joinable sample data, and ensures the singleton
 * {@link AppSettings} row exists. Idempotent: skips tables that already hold data.
 */
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private static final String[] FIRST = { "John", "Jane", "Alice", "Bob", "Charlie", "Diana", "Eve",
            "Frank", "Grace", "Heidi", "Vlad", "Ivan", "Jack", "Maria", "Omar", "Priya" };
    private static final String[] LAST = { "Smith", "Johnson", "Williams", "Jones", "Brown", "Davis",
            "Miller", "Wilson", "Moore", "Taylor", "Popa", "Anderson", "Garcia", "Khan" };
    private static final String[] OFFENSES = { "Fraud", "Theft", "Tax evasion", "Embezzlement", "Forgery" };
    private static final String[] SEVERITIES = { "LOW", "MEDIUM", "HIGH" };

    private final CustomerRepository customers;
    private final CreditHistoryRepository creditHistory;
    private final CriminalRecordRepository criminalRecords;
    private final AppSettingsRepository appSettings;

    public DataLoader(CustomerRepository customers,
            CreditHistoryRepository creditHistory,
            CriminalRecordRepository criminalRecords,
            AppSettingsRepository appSettings) {
        this.customers = customers;
        this.creditHistory = creditHistory;
        this.criminalRecords = criminalRecords;
        this.appSettings = appSettings;
    }

    @Override
    public void run(String... args) {
        seedSettings();
        if (customers.count() > 0) {
            log.info("Customer data already present ({} rows). Skipping seed.", customers.count());
            return;
        }
        seedCustomers();
    }

    private void seedSettings() {
        if (appSettings.findById(AppSettings.SINGLETON_ID).isEmpty()) {
            appSettings.save(new AppSettings());
            log.info("Seeded default app settings.");
        }
    }

    private void seedCustomers() {
        Random rnd = new Random(42); // deterministic sample data
        int n = 200;
        List<Customer> customerBatch = new ArrayList<>();
        List<CreditHistory> historyBatch = new ArrayList<>();
        List<CriminalRecord> criminalBatch = new ArrayList<>();

        log.info("Seeding {} customers with credit history and criminal records...", n);
        for (int i = 0; i < n; i++) {
            String ssn = ssn(rnd);
            String name = FIRST[rnd.nextInt(FIRST.length)] + " " + LAST[rnd.nextInt(LAST.length)];
            customerBatch.add(new Customer(ssn, name,
                    LocalDate.now().minusYears(20 + rnd.nextInt(50)), "USA"));

            int historyScore = 300 + rnd.nextInt(550); // 300-850
            historyBatch.add(new CreditHistory(ssn, historyScore,
                    1 + rnd.nextInt(8), rnd.nextInt(5),
                    BigDecimal.valueOf(1000 + rnd.nextInt(90000)),
                    LocalDate.now().minusDays(rnd.nextInt(365))));

            // ~12% of customers have a criminal record
            if (rnd.nextDouble() < 0.12) {
                criminalBatch.add(new CriminalRecord(ssn,
                        OFFENSES[rnd.nextInt(OFFENSES.length)],
                        SEVERITIES[rnd.nextInt(SEVERITIES.length)],
                        LocalDate.now().minusDays(rnd.nextInt(2000))));
            }
        }

        customers.saveAll(customerBatch);
        creditHistory.saveAll(historyBatch);
        criminalRecords.saveAll(criminalBatch);
        log.info("Seeded {} customers, {} history rows, {} criminal records.",
                customerBatch.size(), historyBatch.size(), criminalBatch.size());
    }

    private static String ssn(Random rnd) {
        return String.format("%03d-%02d-%04d", rnd.nextInt(1000), rnd.nextInt(100), rnd.nextInt(10000));
    }
}
