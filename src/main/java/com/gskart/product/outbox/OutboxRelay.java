package com.gskart.product.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gskart.product.events.ProductEvent;
import com.gskart.product.messaging.DomainEventPublisher;
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

    // Claim (short tx) -> publish to Kafka OUTSIDE any DB transaction, so a pooled connection and
    // the row's lock aren't held across a blocking network call (M3 fix) -> mark the outcome (short
    // tx). A crash between claim and mark-outcome leaves the row IN_PROGRESS;
    // OutboxEventStore#reclaimStuckInProgress (scheduled) recovers those back to PENDING after a
    // grace period. ObjectOptimisticLockingFailureException from claim() propagates to the caller
    // (OutboxEventListener / OutboxRelayScheduler), which treats it as "already claimed elsewhere".
    public void publish(Long outboxEventId) {
        OutboxEvent claimed = outboxEventStore.claim(outboxEventId);
        if (claimed == null) {
            return;
        }

        try {
            ProductEvent event = objectMapper.readValue(claimed.getPayload(), ProductEvent.class);
            domainEventPublisher.publish(claimed.getTopic(), event.getProductId().toString(), event);
            outboxEventStore.markSent(outboxEventId);
        } catch (Exception publishFailure) {
            outboxEventStore.markFailedOrRetry(outboxEventId, publishFailure);
        }
    }
}
