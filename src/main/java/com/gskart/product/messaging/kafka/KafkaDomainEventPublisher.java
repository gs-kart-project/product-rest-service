package com.gskart.product.messaging.kafka;

import com.gskart.product.messaging.DomainEvent;
import com.gskart.product.messaging.DomainEventPublisher;
import com.gskart.product.messaging.EventPublishException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Only active broker adapter when gskart.messaging.broker=kafka (the local default) - keeps a
// future sns-sqs adapter from double-binding the DomainEventPublisher port.
@Slf4j
@Component
@ConditionalOnProperty(name = "gskart.messaging.broker", havingValue = "kafka", matchIfMissing = true)
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaDomainEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(DomainEvent event) {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>(event.getDestination(), event.getKey(), event.getPayload());
        try {
            // Blocking briefly lets the outbox relay know synchronously whether to mark the row
            // SENT or retry it; the timeout keeps a broker outage from hanging the relay/listener.
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Published {} event (key {}) to {}.",
                    event.getEventType(), event.getKey(), event.getDestination());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException(
                    "Interrupted while publishing to " + event.getDestination(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventPublishException(
                    "Failed to publish " + event.getEventType() + " event to " + event.getDestination(), e);
        } catch (RuntimeException e) {
            // Covers failures KafkaTemplate throws synchronously and unchecked - e.g. serialization
            // errors or org.apache.kafka.common.errors.TimeoutException on send-buffer exhaustion -
            // so nothing escapes the publish() port contract.
            throw new EventPublishException(
                    "Failed to publish " + event.getEventType() + " event to " + event.getDestination(), e);
        }
    }
}
