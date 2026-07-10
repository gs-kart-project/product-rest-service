package com.gskart.product.messaging;

// Port (ADR-D5): keeps callers off KafkaTemplate directly so the broker can be swapped (or this
// can converge into gskart-commons, ADR-D16) without touching the outbox/service layer.
public interface DomainEventPublisher {
    void publish(String topic, String key, Object payload);
}
