package com.gskart.product.search.elasticsearch;

import com.gskart.product.events.ProductEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.VersionConflictException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticsearchProductIndexerTest {

    private ElasticsearchOperations elasticsearchOperations;
    private ElasticsearchProductIndexer productIndexer;

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        productIndexer = new ElasticsearchProductIndexer(elasticsearchOperations);
    }

    private ProductEvent event(Instant occurredOn) {
        return ProductEvent.builder()
                .eventId("e1")
                .productId(1L)
                .occurredOn(occurredOn)
                .status("ACTIVE")
                .name("Laptop")
                .description("desc")
                .price(new BigDecimal("999.99"))
                .imageUrl("http://img/1")
                .categoryId(3L)
                .categoryName("Electronics")
                .build();
    }

    private long externalVersion(Instant occurredOn) {
        return occurredOn.getEpochSecond() * 1_000_000_000L + occurredOn.getNano();
    }

    @Test
    void upsertsWhenNoExistingDocument() {
        when(elasticsearchOperations.get("1", ProductDocument.class)).thenReturn(null);

        productIndexer.upsert(event(Instant.now()));

        verify(elasticsearchOperations).save(any(ProductDocument.class));
    }

    @Test
    void upsertsWhenNewEventIsAfterExistingVersion() {
        Instant older = Instant.now().minus(1, ChronoUnit.MINUTES);
        Instant newer = Instant.now();
        ProductDocument existing = new ProductDocument();
        existing.setVersion(externalVersion(older));
        when(elasticsearchOperations.get("1", ProductDocument.class)).thenReturn(existing);

        productIndexer.upsert(event(newer));

        verify(elasticsearchOperations).save(any(ProductDocument.class));
    }

    @Test
    void dropsStaleEventNotAfterExistingVersion() {
        Instant current = Instant.now();
        ProductDocument existing = new ProductDocument();
        existing.setVersion(externalVersion(current));
        when(elasticsearchOperations.get("1", ProductDocument.class)).thenReturn(existing);

        // same timestamp as existing (a duplicate delivery) - must be dropped by the pre-check,
        // without even attempting the write.
        productIndexer.upsert(event(current));

        verify(elasticsearchOperations, never()).save(any(ProductDocument.class));
    }

    @Test
    void dropsReorderedOlderEvent() {
        Instant newer = Instant.now();
        Instant older = newer.minus(1, ChronoUnit.MINUTES);
        ProductDocument existing = new ProductDocument();
        existing.setVersion(externalVersion(newer));
        when(elasticsearchOperations.get("1", ProductDocument.class)).thenReturn(existing);

        productIndexer.upsert(event(older));

        verify(elasticsearchOperations, never()).save(any(ProductDocument.class));
    }

    @Test
    void versionIsSentAsEpochNanosecondsOfOccurredOn() {
        Instant occurredOn = Instant.now();
        when(elasticsearchOperations.get("1", ProductDocument.class)).thenReturn(null);
        org.mockito.ArgumentCaptor<ProductDocument> captor = org.mockito.ArgumentCaptor.forClass(ProductDocument.class);

        productIndexer.upsert(event(occurredOn));

        verify(elasticsearchOperations).save(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(externalVersion(occurredOn));
    }

    // The pre-check above is a fast-path optimization, not the authoritative guard - a stale write
    // that races past it (e.g. two concurrent deliveries) is rejected by ES itself via external
    // versioning, surfacing as VersionConflictException; the indexer must swallow that as an
    // idempotent drop, not propagate it to the Kafka listener (which would otherwise retry/DLT a
    // non-error).
    @Test
    void dropsWriteThatLosesTheRaceToEsExternalVersioning() {
        when(elasticsearchOperations.get("1", ProductDocument.class)).thenReturn(null);
        doThrow(new VersionConflictException("Version conflict"))
                .when(elasticsearchOperations).save(any(ProductDocument.class));

        assertThatCode(() -> productIndexer.upsert(event(Instant.now()))).doesNotThrowAnyException();
    }

    @Test
    void deletedStatusIsUpsertedNotHardDeleted() {
        when(elasticsearchOperations.get("1", ProductDocument.class)).thenReturn(null);
        ProductEvent deleteEvent = ProductEvent.builder()
                .eventId("e1")
                .productId(1L)
                .occurredOn(Instant.now())
                .status("DELETED")
                .name("Laptop")
                .price(new BigDecimal("999.99"))
                .categoryId(3L)
                .categoryName("Electronics")
                .build();

        productIndexer.upsert(deleteEvent);

        verify(elasticsearchOperations, never()).delete(org.mockito.ArgumentMatchers.eq("1"), any(Class.class));
        verify(elasticsearchOperations).save(any(ProductDocument.class));
    }
}
