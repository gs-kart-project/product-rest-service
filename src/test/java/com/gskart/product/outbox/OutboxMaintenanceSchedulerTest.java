package com.gskart.product.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxMaintenanceSchedulerTest {

    private OutboxEventRepository outboxEventRepository;
    private OutboxMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        scheduler = new OutboxMaintenanceScheduler(outboxEventRepository, 168L);
    }

    @Test
    void purgesSentRowsOlderThanRetentionWindow() {
        when(outboxEventRepository.deleteByStatusAndSentOnBefore(eq(OutboxEvent.OutboxStatus.SENT), any(OffsetDateTime.class)))
                .thenReturn(5L);
        when(outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.FAILED)).thenReturn(0L);

        scheduler.purgeSentEvents();

        verify(outboxEventRepository).deleteByStatusAndSentOnBefore(eq(OutboxEvent.OutboxStatus.SENT), any(OffsetDateTime.class));
    }

    @Test
    void countsFailedRowsForVisibilityEvenWhenNothingToPurge() {
        when(outboxEventRepository.deleteByStatusAndSentOnBefore(eq(OutboxEvent.OutboxStatus.SENT), any(OffsetDateTime.class)))
                .thenReturn(0L);
        when(outboxEventRepository.countByStatus(OutboxEvent.OutboxStatus.FAILED)).thenReturn(3L);

        scheduler.purgeSentEvents();

        verify(outboxEventRepository).countByStatus(OutboxEvent.OutboxStatus.FAILED);
    }
}
