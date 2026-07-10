package com.gskart.product.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventStoreTest {

    private OutboxEventRepository outboxEventRepository;
    private OutboxEventStore outboxEventStore;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        outboxEventStore = new OutboxEventStore(outboxEventRepository, 3);
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setStatus(OutboxEvent.OutboxStatus.PENDING);
        event.setAttempts(0);
        return event;
    }

    @Test
    void claimReturnsNullWhenRowMissing() {
        when(outboxEventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(outboxEventStore.claim(99L)).isNull();
        verify(outboxEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void claimReturnsNullWhenRowNotPending() {
        OutboxEvent sent = pendingEvent();
        sent.setStatus(OutboxEvent.OutboxStatus.SENT);
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(sent));

        assertThat(outboxEventStore.claim(1L)).isNull();
        verify(outboxEventRepository, never()).saveAndFlush(any());
    }

    @Test
    void claimTransitionsToInProgressAndStampsClaimedOn() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(outboxEventRepository.saveAndFlush(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        OutboxEvent claimed = outboxEventStore.claim(1L);

        assertThat(claimed.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.IN_PROGRESS);
        assertThat(claimed.getClaimedOn()).isNotNull();
    }

    @Test
    void markSentSetsStatusAndTimestamp() {
        OutboxEvent event = pendingEvent();
        event.setStatus(OutboxEvent.OutboxStatus.IN_PROGRESS);
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        outboxEventStore.markSent(1L);

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.SENT);
        assertThat(event.getSentOn()).isNotNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    void markSentIsNoOpWhenRowMissing() {
        when(outboxEventRepository.findById(99L)).thenReturn(Optional.empty());

        outboxEventStore.markSent(99L);

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void markFailedOrRetryBelowMaxAttemptsGoesBackToPending() {
        OutboxEvent event = pendingEvent();
        event.setStatus(OutboxEvent.OutboxStatus.IN_PROGRESS);
        event.setAttempts(0);
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        outboxEventStore.markFailedOrRetry(1L, new RuntimeException("broker unreachable"));

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    @Test
    void markFailedOrRetryAtMaxAttemptsMarksFailed() {
        OutboxEvent event = pendingEvent();
        event.setStatus(OutboxEvent.OutboxStatus.IN_PROGRESS);
        event.setAttempts(2); // 3rd attempt (maxAttempts=3) should permanently fail
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        outboxEventStore.markFailedOrRetry(1L, new RuntimeException("broker unreachable"));

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(3);
    }

    @Test
    void reclaimStuckInProgressResetsRowsBackToPending() {
        OutboxEvent stuck = pendingEvent();
        stuck.setStatus(OutboxEvent.OutboxStatus.IN_PROGRESS);
        OffsetDateTime threshold = OffsetDateTime.now();
        when(outboxEventRepository.findByStatusAndClaimedOnBefore(OutboxEvent.OutboxStatus.IN_PROGRESS, threshold))
                .thenReturn(List.of(stuck));

        int reclaimed = outboxEventStore.reclaimStuckInProgress(threshold);

        assertThat(reclaimed).isEqualTo(1);
        assertThat(stuck.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
        verify(outboxEventRepository).saveAll(anyList());
    }

    @Test
    void reclaimStuckInProgressReturnsZeroWhenNothingStuck() {
        OffsetDateTime threshold = OffsetDateTime.now();
        when(outboxEventRepository.findByStatusAndClaimedOnBefore(OutboxEvent.OutboxStatus.IN_PROGRESS, threshold))
                .thenReturn(List.of());

        assertThat(outboxEventStore.reclaimStuckInProgress(threshold)).isZero();
    }
}
