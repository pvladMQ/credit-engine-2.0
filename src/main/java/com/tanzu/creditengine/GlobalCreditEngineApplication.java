package com.tanzu.creditengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Global Credit Scoring Engine
 * 
 * A demonstration of the "Data Hub" architecture for Tanzu Experience Day.
 * This application showcases the integration of:
 * - PostgreSQL for transactional data storage
 * - RabbitMQ for asynchronous message processing
 * - Valkey (Redis) for sub-second global data retrieval
 */
@SpringBootApplication
public class GlobalCreditEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlobalCreditEngineApplication.class, args);
    }
}
