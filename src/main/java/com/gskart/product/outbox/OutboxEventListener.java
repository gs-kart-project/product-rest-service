package com.gskart.product.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Immediate publish path: fires only after the writing transaction actually commits, and runs
// asynchronously so the outbox row hits Kafka near-instantly without the request thread waiting
// on it. OutboxRelayScheduler is the fallback for anything this path misses (crash, publish error).
@Component
public class OutboxEventListener {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventListener.class);

    private final OutboxRelay outboxRelay;

    public OutboxEventListener(OutboxRelay outboxRelay) {
        this.outboxRelay = outboxRelay;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventReady(OutboxEventReadyEvent event) {
        for (Long outboxEventId : event.getOutboxEventIds()) {
            try {
                outboxRelay.publish(outboxEventId);
            } catch (ObjectOptimisticLockingFailureException alreadyClaimed) {
                log.debug("Outbox event {} already claimed by another path", outboxEventId);
            } catch (Exception unexpected) {
                log.error("Unexpected error publishing outbox event {} - the fallback relay will retry",
                        outboxEventId, unexpected);
            }
        }
    }
}
