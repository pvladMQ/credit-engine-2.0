package com.tanzu.creditengine.messaging;

/**
 * Publishes an inbound scoring request for asynchronous processing. The cloud profile
 * enqueues it on RabbitMQ; the local profile processes it in-process so the UI works
 * without a broker.
 */
public interface ScoreRequestPublisher {

    void publish(ScoreRequest request);
}
