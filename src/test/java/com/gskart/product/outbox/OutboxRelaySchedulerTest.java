package com.gskart.product.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelaySchedulerTest {

    private OutboxEventRepository outboxEventRepository;
    private OutboxRelay outboxRelay;
    private OutboxEventStore outboxEventStore;
    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        outboxRelay = mock(OutboxRelay.class);
        outboxEventStore = mock(OutboxEventStore.class);
        scheduler = new OutboxRelayScheduler(outboxEventRepository, outboxRelay, outboxEventStore, 50, 60000L);
    }

    private OutboxEvent pendingEvent(Long id) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setStatus(OutboxEvent.OutboxStatus.PENDING);
        return event;
    }

    @Test
    void sweepPublishesEveryPendingRowInTheBatch() {
        when(outboxEventRepository.findByStatusOrderByCreatedOnAsc(eq(OutboxEvent.OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(pendingEvent(1L), pendingEvent(2L)));

        scheduler.sweepPendingEvents();

        verify(outboxRelay).publish(1L);
        verify(outboxRelay).publish(2L);
    }

    @Test
    void sweepSwallowsOptimisticLockConflictAsAlreadyClaimed() {
        when(outboxEventRepository.findByStatusOrderByCreatedOnAsc(eq(OutboxEvent.OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(pendingEvent(1L)));
        doThrow(new ObjectOptimisticLockingFailureException(OutboxEvent.class, 1L))
                .when(outboxRelay).publish(1L);

        scheduler.sweepPendingEvents();

        verify(outboxRelay).publish(1L);
    }

    @Test
    void sweepContinuesToNextRowAfterUnexpectedError() {
        when(outboxEventRepository.findByStatusOrderByCreatedOnAsc(eq(OutboxEvent.OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(pendingEvent(1L), pendingEvent(2L)));
        doThrow(new RuntimeException("db hiccup")).when(outboxRelay).publish(1L);

        scheduler.sweepPendingEvents();

        verify(outboxRelay).publish(1L);
        verify(outboxRelay).publish(2L);
    }

    @Test
    void reclaimDelegatesToStoreWithThresholdBasedOnConfiguredWindow() {
        when(outboxEventStore.reclaimStuckInProgress(any(OffsetDateTime.class))).thenReturn(2);

        scheduler.reclaimStuckInProgressEvents();

        verify(outboxEventStore, times(1)).reclaimStuckInProgress(any(OffsetDateTime.class));
    }
}
