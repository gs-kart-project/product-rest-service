package com.gskart.product.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

// This lives in its own Spring bean, not as methods on OutboxRelay, because Spring can't make
// @Transactional work on a class calling its own methods. Splitting it out this way also means the
// blocking Kafka send in OutboxRelay never happens while holding a DB connection or row lock.
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

    // Uses the row's @Version lock so two things trying to claim the same event at once (the
    // after-commit publish and the fallback sweep) can't both win - whichever loses gets an
    // ObjectOptimisticLockingFailureException instead of the event getting published twice.
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

    // If the app crashes between claim() and marking the outcome, a row can get stuck IN_PROGRESS
    // forever. This puts anything stuck past the grace period back to PENDING so the fallback
    // sweep picks it up again.
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
