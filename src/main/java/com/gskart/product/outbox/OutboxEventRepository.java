package com.gskart.product.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByStatusOrderByCreatedOnAsc(OutboxEvent.OutboxStatus status, Pageable pageable);

    // Recovery sweep (M3 fix): rows left IN_PROGRESS by a crash between claim and mark-outcome.
    List<OutboxEvent> findByStatusAndClaimedOnBefore(OutboxEvent.OutboxStatus status, OffsetDateTime threshold);

    // Housekeeping (M4 fix): bound the table's growth and surface stuck failures.
    long deleteByStatusAndSentOnBefore(OutboxEvent.OutboxStatus status, OffsetDateTime threshold);

    long countByStatus(OutboxEvent.OutboxStatus status);
}
