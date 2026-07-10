package com.gskart.product.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

// Persistence-only operations for the outbox claim/publish/mark-outcome lifecycle, kept in a
// separate Spring bean (not private methods on OutboxRelay) so each step below runs in its own
// short transaction via normal @Transactional proxying - Spring can't intercept self-invoked
// same-class method calls, so splitting claim from mark-outcome (M3, so the blocking Kafka send in
// between never holds a DB connection/row lock) needs a real bean boundary, not just extracted
// methods.
@Component
public class OutboxEventStore {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventStore.class);

    private final OutboxEventRepository outboxEventRepository;
    private final int maxAttempts;

    public OutboxEventStore(OutboxEventRepository outboxEventRepository,
                             @Value("${gskart.product.outbox.relay.max-attempts}") int maxAttempts) {
        this.outboxEventRepository = outboxEventRepository;
        this.maxAttempts = maxAttempts;
    }

    // Claims the row (PENDING -> IN_PROGRESS) via the entity's @Version optimistic lock, so the
    // immediate AFTER_COMMIT path and the scheduled fallback sweep can race for the same row
    // without ever both publishing it: the loser's flush throws
    // ObjectOptimisticLockingFailureException, which propagates to the caller.
    @Transactional
    public OutboxEvent claim(Long outboxEventId) {
        OutboxEvent outboxEvent = outboxEventRepository.findById(outboxEventId).orElse(null);
        if (outboxEvent == null || outboxEvent.getStatus() != OutboxEvent.OutboxStatus.PENDING) {
            return null;
        }
        outboxEvent.setStatus(OutboxEvent.OutboxStatus.IN_PROGRESS);
        outboxEvent.setClaimedOn(OffsetDateTime.now(ZoneOffset.UTC));
        return outboxEventRepository.saveAndFlush(outboxEvent);
    }

    @Transactional
    public void markSent(Long outboxEventId) {
        outboxEventRepository.findById(outboxEventId).ifPresent(outboxEvent -> {
            outboxEvent.setStatus(OutboxEvent.OutboxStatus.SENT);
            outboxEvent.setSentOn(OffsetDateTime.now(ZoneOffset.UTC));
            outboxEventRepository.save(outboxEvent);
        });
    }

    @Transactional
    public void markFailedOrRetry(Long outboxEventId, Throwable publishFailure) {
        outboxEventRepository.findById(outboxEventId).ifPresent(outboxEvent -> {
            int attempts = outboxEvent.getAttempts() + 1;
            log.warn("Failed to publish outbox event {} (attempt {})", outboxEventId, attempts, publishFailure);
            outboxEvent.setAttempts(attempts);
            outboxEvent.setStatus(attempts >= maxAttempts
                    ? OutboxEvent.OutboxStatus.FAILED
                    : OutboxEvent.OutboxStatus.PENDING);
            outboxEventRepository.save(outboxEvent);
        });
    }

    // Recovery for rows left IN_PROGRESS by a crash between claim() and markSent/markFailedOrRetry
    // (M3 fix): reclaims anything stuck past the grace period back to PENDING so the fallback sweep
    // picks it up again.
    @Transactional
    public int reclaimStuckInProgress(OffsetDateTime claimedBefore) {
        List<OutboxEvent> stuck = outboxEventRepository.findByStatusAndClaimedOnBefore(
                OutboxEvent.OutboxStatus.IN_PROGRESS, claimedBefore);
        for (OutboxEvent outboxEvent : stuck) {
            outboxEvent.setStatus(OutboxEvent.OutboxStatus.PENDING);
        }
        outboxEventRepository.saveAll(stuck);
        return stuck.size();
    }
}
