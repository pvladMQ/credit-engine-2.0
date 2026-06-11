package com.tanzu.creditengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Credit Engine v2
 *
 * Spring Boot 3.5 credit scoring engine on Tanzu Platform, integrating:
 * - PostgreSQL for transactional data storage (source of truth)
 * - RabbitMQ for asynchronous request processing
 * - Valkey (Redis protocol) for sub-second score caching
 * - GenAI (credit-chat) for natural-language querying via Spring AI
 */
@SpringBootApplication
public class CreditEngineV2Application {

    public static void main(String[] args) {
        SpringApplication.run(CreditEngineV2Application.class, args);
    }
}
