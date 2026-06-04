package com.tanzu.creditengine.messaging;

import com.tanzu.creditengine.service.CreditScoreCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Cloud profile: consumes scoring requests from RabbitMQ and runs them through the
 * scoring pipeline. The blocking {@code Thread.sleep} the original listener used to
 * simulate latency has been removed — it pinned listener threads and was a contributor
 * to the reported CPU spikes.
 */
@Component
@Profile("cloud")
public class CreditApplicationListener {

    private static final Logger log = LoggerFactory.getLogger(CreditApplicationListener.class);

    private final CreditScoreCalculator calculator;

    public CreditApplicationListener(CreditScoreCalculator calculator) {
        this.calculator = calculator;
    }

    @RabbitListener(queues = "${credit-engine.queue.name:application-requests}")
    public void onMessage(ScoreRequest request) {
        log.info("Received scoring request for {}", request.getSsn());
        try {
            calculator.process(request);
        } catch (Exception e) {
            log.error("Failed to process scoring request for {}", request.getSsn(), e);
            throw e; // trigger broker requeue / DLQ handling
        }
    }
}
