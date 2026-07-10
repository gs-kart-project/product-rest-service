package com.gskart.product.messaging.kafka;

import com.gskart.product.messaging.DomainEventPublishException;
import com.gskart.product.messaging.DomainEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaDomainEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(String topic, String key, Object payload) {
        try {
            // Blocking briefly lets the outbox relay know synchronously whether to mark the row
            // SENT or retry it; the timeout keeps a broker outage from hanging the relay/listener.
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new DomainEventPublishException("Failed to publish event to topic " + topic, e);
        }
    }
}
