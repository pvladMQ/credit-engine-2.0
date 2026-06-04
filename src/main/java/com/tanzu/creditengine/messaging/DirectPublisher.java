package com.tanzu.creditengine.messaging;

import com.tanzu.creditengine.service.CreditScoreCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local profile: no broker available, so requests are scored in-process. Keeps the same
 * publish/consume seam as the cloud path so controllers are profile-agnostic.
 */
@Component
@Profile("local")
public class DirectPublisher implements ScoreRequestPublisher {

    private static final Logger log = LoggerFactory.getLogger(DirectPublisher.class);

    private final CreditScoreCalculator calculator;

    public DirectPublisher(CreditScoreCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public void publish(ScoreRequest request) {
        log.info("[local] Processing scoring request in-process for {}", request.getSsn());
        calculator.process(request);
    }
}
