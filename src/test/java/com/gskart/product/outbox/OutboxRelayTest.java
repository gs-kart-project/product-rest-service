package com.gskart.product.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gskart.product.messaging.DomainEvent;
import com.gskart.product.messaging.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxRelayTest {

    private OutboxEventStore outboxEventStore;
    private DomainEventPublisher domainEventPublisher;
    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxEventStore = mock(OutboxEventStore.class);
        domainEventPublisher = mock(DomainEventPublisher.class);
        outboxRelay = new OutboxRelay(outboxEventStore, domainEventPublisher, new ObjectMapper());
    }

    private OutboxEvent claimedEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setTopic("product.events.v1");
        event.setAggregateType("Product");
        event.setStatus(OutboxEvent.OutboxStatus.IN_PROGRESS);
        event.setPayload("{\"productId\":5,\"status\":\"ACTIVE\"}");
        return event;
    }

    @Test
    void doesNothingWhenClaimYieldsNothing() {
        when(outboxEventStore.claim(1L)).thenReturn(null);

        outboxRelay.publish(1L);

        verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
        verify(outboxEventStore, never()).markSent(anyLong());
        verify(outboxEventStore, never()).markFailedOrRetry(anyLong(), any());
    }

    @Test
    void claimsThenPublishesOutsideAnyTransactionAndMarksSent() {
        when(outboxEventStore.claim(1L)).thenReturn(claimedEvent());

        outboxRelay.publish(1L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getDestination()).isEqualTo("product.events.v1");
        assertThat(captor.getValue().getKey()).isEqualTo("5");
        assertThat(captor.getValue().getEventType()).isEqualTo("Product");
        verify(outboxEventStore).markSent(1L);
        verify(outboxEventStore, never()).markFailedOrRetry(anyLong(), any());
    }

    @Test
    void claimConflictPropagatesSoCallerTreatsItAsAlreadyHandled() {
        when(outboxEventStore.claim(1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(OutboxEvent.class, 1L));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> outboxRelay.publish(1L));
        verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
    }

    @Test
    void publishFailureMarksFailedOrRetryInsteadOfSent() {
        when(outboxEventStore.claim(1L)).thenReturn(claimedEvent());
        doThrow(new RuntimeException("broker unreachable"))
                .when(domainEventPublisher).publish(any(DomainEvent.class));

        outboxRelay.publish(1L);

        verify(outboxEventStore).markFailedOrRetry(eq(1L), any(RuntimeException.class));
        verify(outboxEventStore, never()).markSent(anyLong());
    }

    @Test
    void malformedPayloadIsTreatedAsPublishFailureNotPropagated() {
        OutboxEvent malformed = claimedEvent();
        malformed.setPayload("not valid json");
        when(outboxEventStore.claim(1L)).thenReturn(malformed);

        outboxRelay.publish(1L);

        verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
        verify(outboxEventStore).markFailedOrRetry(eq(1L), any());
    }
}
