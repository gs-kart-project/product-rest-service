package com.gskart.product.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// outbox_events gets a new row on every write and nothing ever deletes them, so this job cleans
// out old SENT rows on a schedule and logs how many are stuck FAILED, so those don't go unnoticed
// and end up needing a manual replay/reindex.
@Component
public class OutboxMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxMaintenanceScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final long sentRetentionHours;

    public OutboxMaintenanceScheduler(OutboxEventRepository outboxEventRepository,
                                       @Value("${gskart.product.outbox.purge.sent-retention-hours}") long sentRetentionHours) {
        this.outboxEventRepository = outboxEventRepository;
        this.sentRetentionHours = sentRetentionHours;
    }

    @Scheduled(fixedDelayString = "${gskart.product.outbox.purge.delay-ms}")
    @Transactional
    public void purgeSentEvents() {
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusHours(sentRetentionHours);
        long purged = outboxEventRepository.deleteByStatusAndSentOnBefore(OutboxEvent.OutboxStatus.SENT, threshold);
        if (purged > 0) {
            log.info("Purged {} SENT outbox event(s) older than {}h", purged, sentRetentionHours);
        }

        long failedCount = outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.FAILED);
        if (failedCount > 0) {
            log.warn("{} outbox event(s) are in terminal FAILED state and need investigation/replay", failedCount);
        }
    }
}
