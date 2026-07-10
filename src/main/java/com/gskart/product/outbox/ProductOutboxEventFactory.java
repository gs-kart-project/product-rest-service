package com.gskart.product.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gskart.product.entities.Category;
import com.gskart.product.entities.Product;
import com.gskart.product.events.ProductEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class ProductOutboxEventFactory {

    private final ObjectMapper objectMapper;
    private final String topic;

    public ProductOutboxEventFactory(ObjectMapper objectMapper,
                                      @Value("${gskart.product.events.topic}") String topic) {
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public OutboxEvent forProduct(Product product, Category category) {
        ProductEvent productEvent = ProductEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .productId(product.getId())
                .occurredOn(Instant.now())
                .status(product.getStatus().name())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .build();
        return toOutboxEvent(productEvent);
    }

    private OutboxEvent toOutboxEvent(ProductEvent productEvent) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Product");
        outboxEvent.setAggregateId(productEvent.getProductId());
        outboxEvent.setTopic(topic);
        outboxEvent.setStatus(OutboxEvent.OutboxStatus.PENDING);
        outboxEvent.setAttempts(0);
        outboxEvent.setCreatedOn(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            outboxEvent.setPayload(objectMapper.writeValueAsString(productEvent));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize product event for product " + productEvent.getProductId(), e);
        }
        return outboxEvent;
    }
}
