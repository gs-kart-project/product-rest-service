package com.gskart.product.messaging.kafka;

import com.gskart.product.messaging.DomainEventPublishException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaDomainEventPublisherTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new KafkaDomainEventPublisher(kafkaTemplate);
    }

    @Test
    void delegatesToKafkaTemplateAndBlocksUntilAcknowledged() {
        SendResult<String, Object> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send("product.events.v1", "5", "payload"))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publish("product.events.v1", "5", "payload");

        verify(kafkaTemplate).send(eq("product.events.v1"), eq("5"), any());
    }

    @Test
    void wrapsBrokerFailureInDomainEventPublishException() {
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send("product.events.v1", "5", "payload")).thenReturn(failed);

        assertThatThrownBy(() -> publisher.publish("product.events.v1", "5", "payload"))
                .isInstanceOf(DomainEventPublishException.class)
                .hasMessageContaining("product.events.v1")
                .hasCauseInstanceOf(java.util.concurrent.ExecutionException.class);
    }

    @Test
    void wrapsSendTimeoutInDomainEventPublishException() {
        // Never completes -> KafkaDomainEventPublisher's get(5, SECONDS) times out. Uses a fresh
        // (never-completed) future rather than sleeping the test for the real 5s timeout window.
        CompletableFuture<SendResult<String, Object>> neverCompletes =
                mock(CompletableFuture.class, invocation -> {
                    throw new java.util.concurrent.TimeoutException();
                });
        when(kafkaTemplate.send("product.events.v1", "5", "payload")).thenReturn(neverCompletes);

        assertThatThrownBy(() -> publisher.publish("product.events.v1", "5", "payload"))
                .isInstanceOf(DomainEventPublishException.class);
    }
}
