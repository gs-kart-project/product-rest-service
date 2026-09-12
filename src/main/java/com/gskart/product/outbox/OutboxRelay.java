package com.gskart.product.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gskart.product.events.ProductEvent;
import com.gskart.commons.messaging.DomainEvent;
import com.gskart.commons.messaging.DomainEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class OutboxRelay {

    private final OutboxEventStore outboxEventStore;
    private final DomainEventPublisher domainEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxEventStore outboxEventStore,
                        DomainEventPublisher domainEventPublisher,
                        ObjectMapper objectMapper) {
        this.outboxEventStore = outboxEventStore;
        this.domainEventPublisher = domainEventPublisher;
        this.objectMapper = objectMapper;
    }

    // Claim/publish/mark are separate short transactions so a lock isn't held across the network
    // call. A crash between claim and mark leaves the row IN_PROGRESS for reclaimStuckInProgress
    // to pick up; a locking failure from claim() means another caller got there first.
    public void publish(Long outboxEventId) {
        OutboxEvent claimed = outboxEventStore.claim(outboxEventId);
        if (claimed == null) {
            return;
        }

        try {
            ProductEvent event = objectMapper.readValue(claimed.getPayload(), ProductEvent.class);
            // Log/trace label only - default it so an unset column doesn't log as "null".
            String eventType = claimed.getAggregateType() != null ? claimed.getAggregateType() : "Product";
            DomainEvent domainEvent = DomainEvent.builder()
                    .destination(claimed.getTopic())
                    .key(event.getProductId().toString())
                    .eventType(eventType)
                    .payload(event)
                    .build();
            domainEventPublisher.publish(domainEvent);
            outboxEventStore.markSent(outboxEventId);
        } catch (Exception publishFailure) {
            outboxEventStore.markFailedOrRetry(outboxEventId, publishFailure);
        }
    }
}
