package com.gskart.product.messaging.kafka;

import com.gskart.product.messaging.DomainEvent;
import com.gskart.product.messaging.EventPublishException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaDomainEventPublisherTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaDomainEventPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new KafkaDomainEventPublisher(kafkaTemplate);
    }

    private DomainEvent event(Object payload) {
        return DomainEvent.builder()
                .destination("product.events.v1")
                .key("5")
                .eventType("Product")
                .payload(payload)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void delegatesToKafkaTemplateAndBlocksUntilAcknowledged() {
        SendResult<String, Object> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        Object payload = new Object();

        publisher.publish(event(payload));

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("product.events.v1");
        assertThat(captor.getValue().key()).isEqualTo("5");
        assertThat(captor.getValue().value()).isSameAs(payload);
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrapsBrokerFailureInEventPublishException() {
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        assertThatThrownBy(() -> publisher.publish(event("payload")))
                .isInstanceOf(EventPublishException.class)
                .hasMessageContaining("product.events.v1")
                .hasCauseInstanceOf(java.util.concurrent.ExecutionException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrapsSendTimeoutInEventPublishException() {
        // Never completes -> KafkaDomainEventPublisher's get(5, SECONDS) times out. Uses a fresh
        // (never-completed) future rather than sleeping the test for the real 5s timeout window.
        CompletableFuture<SendResult<String, Object>> neverCompletes =
                mock(CompletableFuture.class, invocation -> {
                    throw new java.util.concurrent.TimeoutException();
                });
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(neverCompletes);

        assertThatThrownBy(() -> publisher.publish(event("payload")))
                .isInstanceOf(EventPublishException.class);
    }
}
