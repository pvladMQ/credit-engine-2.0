package com.tanzu.creditengine.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Cloud profile: publishes scoring requests to the RabbitMQ {@code credit-msg} queue.
 */
@Component
@Profile("cloud")
public class RabbitPublisher implements ScoreRequestPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${credit-engine.queue.name:application-requests}")
    private String queueName;

    public RabbitPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(ScoreRequest request) {
        log.info("Enqueuing scoring request for {}", request.getSsn());
        rabbitTemplate.convertAndSend(queueName, request);
    }
}
