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
 * criminal_records) with coherent, joinable sample data spanning several nationalities
 * (French, British, Spanish, Italian, Pakistani, Arabic), and ensures the singleton
 * {@link AppSettings} row exists.
 *
 * <p>The bulk 200-row seed only runs on an empty database. A small idempotent
 * "showcase" set of named customers is inserted on every startup (by SSN, if missing) so
 * the international names appear even on an already-populated database.
 */
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    /** A nationality with its name pools and country label. */
    private record Nationality(String country, String[] first, String[] last) {
    }

    private static final Nationality[] NATIONALITIES = {
            new Nationality("France",
                    new String[] { "Jean", "Pierre", "Camille", "Élodie", "Luc", "Margaux", "Antoine", "Chloé" },
                    new String[] { "Dubois", "Lefebvre", "Moreau", "Laurent", "Girard", "Bernard", "Rousseau", "Fontaine" }),
            new Nationality("United Kingdom",
                    new String[] { "Oliver", "Amelia", "Harry", "Charlotte", "George", "Emily", "Jack", "Sophie" },
                    new String[] { "Smith", "Jones", "Taylor", "Brown", "Wilson", "Evans", "Walker", "Wright" }),
            new Nationality("Spain",
                    new String[] { "Carlos", "María", "Javier", "Lucía", "Sofía", "Diego", "Carmen", "Pablo" },
                    new String[] { "García", "Martínez", "Rodríguez", "Fernández", "López", "Sánchez", "Romero", "Torres" }),
            new Nationality("Italy",
                    new String[] { "Marco", "Giulia", "Lorenzo", "Francesca", "Matteo", "Chiara", "Alessandro", "Sofia" },
                    new String[] { "Rossi", "Russo", "Ferrari", "Esposito", "Bianchi", "Romano", "Greco", "Conti" }),
            new Nationality("Pakistan",
                    new String[] { "Ahmed", "Ayesha", "Bilal", "Fatima", "Usman", "Zara", "Imran", "Hina" },
                    new String[] { "Khan", "Malik", "Hussain", "Iqbal", "Raza", "Sheikh", "Butt", "Chaudhry" }),
            new Nationality("United Arab Emirates",
                    new String[] { "Omar", "Layla", "Yusuf", "Aisha", "Khalid", "Mariam", "Tariq", "Noor" },
                    new String[] { "Al-Farsi", "Haddad", "Nasser", "Khalil", "Mansour", "Saleh", "Aziz", "Hassan" }),
    };

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
        if (customers.count() == 0) {
            seedCustomers();
        } else {
            log.info("Customer data already present ({} rows). Skipping bulk seed.", customers.count());
        }
        ensureShowcaseCustomers();
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

        log.info("Seeding {} customers (mixed nationalities) with credit history and criminal records...", n);
        for (int i = 0; i < n; i++) {
            Nationality nat = NATIONALITIES[rnd.nextInt(NATIONALITIES.length)];
            String ssn = ssn(rnd);
            String name = nat.first()[rnd.nextInt(nat.first().length)] + " "
                    + nat.last()[rnd.nextInt(nat.last().length)];
            customerBatch.add(new Customer(ssn, name,
                    LocalDate.now().minusYears(20 + rnd.nextInt(50)), nat.country()));

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

    /**
     * Idempotently inserts a fixed set of named international customers (one per
     * nationality) with deterministic SSNs, so the names are present even when the bulk
     * seed was skipped on an existing database. Each gets a credit-history row.
     */
    private void ensureShowcaseCustomers() {
        // ssn, full name, country, history score, delinquencies
        Object[][] showcase = {
                { "900-01-0001", "Camille Dubois", "France", 812, 0 },
                { "900-02-0002", "Amelia Walker", "United Kingdom", 705, 1 },
                { "900-03-0003", "Javier Martínez", "Spain", 648, 2 },
                { "900-04-0004", "Giulia Ferrari", "Italy", 770, 0 },
                { "900-05-0005", "Ayesha Khan", "Pakistan", 583, 3 },
                { "900-06-0006", "Omar Al-Farsi", "United Arab Emirates", 731, 1 },
                { "900-07-0007", "Pierre Lefebvre", "France", 690, 1 },
                { "900-08-0008", "Harry Wilson", "United Kingdom", 540, 4 },
                { "900-09-0009", "Lucía Rodríguez", "Spain", 798, 0 },
                { "900-10-0010", "Matteo Romano", "Italy", 612, 2 },
                { "900-11-0011", "Bilal Hussain", "Pakistan", 660, 1 },
                { "900-12-0012", "Layla Haddad", "United Arab Emirates", 845, 0 },
        };

        int added = 0;
        for (Object[] row : showcase) {
            String ssn = (String) row[0];
            if (customers.existsById(ssn)) {
                continue;
            }
            String name = (String) row[1];
            String country = (String) row[2];
            int historyScore = (int) row[3];
            int delinquencies = (int) row[4];

            customers.save(new Customer(ssn, name, LocalDate.now().minusYears(35), country));
            creditHistory.save(new CreditHistory(ssn, historyScore, 3, delinquencies,
                    BigDecimal.valueOf(15000), LocalDate.now().minusDays(30)));
            added++;
        }
        if (added > 0) {
            log.info("Inserted {} showcase international customers.", added);
        }
    }

    private static String ssn(Random rnd) {
        return String.format("%03d-%02d-%04d", rnd.nextInt(1000), rnd.nextInt(100), rnd.nextInt(10000));
    }
}
