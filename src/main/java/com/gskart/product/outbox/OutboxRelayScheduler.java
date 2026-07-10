package com.gskart.product.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Fallback sweep: catches rows the immediate AFTER_COMMIT path missed (app crash between commit
// and publish, or a transient publish failure left the row PENDING again).
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxRelay outboxRelay;
    private final OutboxEventStore outboxEventStore;
    private final int batchSize;
    private final long stuckRecoveryThresholdMs;

    public OutboxRelayScheduler(OutboxEventRepository outboxEventRepository,
                                 OutboxRelay outboxRelay,
                                 OutboxEventStore outboxEventStore,
                                 @Value("${gskart.product.outbox.relay.batch-size}") int batchSize,
                                 @Value("${gskart.product.outbox.relay.stuck-recovery-threshold-ms}") long stuckRecoveryThresholdMs) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxRelay = outboxRelay;
        this.outboxEventStore = outboxEventStore;
        this.batchSize = batchSize;
        this.stuckRecoveryThresholdMs = stuckRecoveryThresholdMs;
    }

    @Scheduled(fixedDelayString = "${gskart.product.outbox.relay.fixed-delay-ms}")
    public void sweepPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedOnAsc(
                OutboxEvent.OutboxStatus.PENDING, PageRequest.of(0, batchSize));
        for (OutboxEvent outboxEvent : pending) {
            try {
                outboxRelay.publish(outboxEvent.getId());
            } catch (ObjectOptimisticLockingFailureException alreadyClaimed) {
                log.debug("Outbox event {} already claimed by another path", outboxEvent.getId());
            } catch (Exception unexpected) {
                log.error("Unexpected error publishing outbox event {} during fallback sweep",
                        outboxEvent.getId(), unexpected);
            }
        }
    }

    // Recovers rows a crash left stuck IN_PROGRESS between claim() and mark-outcome (M3 fix) -
    // without this, such a row is claimed forever and never republished or retried.
    @Scheduled(fixedDelayString = "${gskart.product.outbox.relay.stuck-recovery-delay-ms}")
    public void reclaimStuckInProgressEvents() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minus(stuckRecoveryThresholdMs, ChronoUnit.MILLIS);
        int reclaimed = outboxEventStore.reclaimStuckInProgress(threshold);
        if (reclaimed > 0) {
            log.warn("Reclaimed {} outbox event(s) stuck IN_PROGRESS (likely an app crash mid-publish)", reclaimed);
        }
    }
}
