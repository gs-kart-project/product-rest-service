package com.gskart.product;

import com.gskart.product.messaging.DomainEventPublisher;
import com.gskart.product.messaging.kafka.KafkaDomainEventPublisher;
import com.gskart.product.messaging.kafka.ProductEventListener;
import com.gskart.product.search.ProductIndexer;
import com.gskart.product.search.elasticsearch.ElasticsearchProductIndexer;
import com.gskart.product.search.elasticsearch.ElasticsearchSearchService;
import com.gskart.product.search.elasticsearch.ProductDocument;
import com.gskart.product.services.ISearchService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Proves the ADR-D5 seam contract that application.properties documents: under the local
// defaults exactly one adapter is active per port, and selecting gskart.messaging.broker /
// gskart.search.engine values with no shipped adapter (sns-sqs, opensearch) is NOT a no-op - it
// leaves the port without a bean. *PortConsumer below stands in for the real consumers that
// require the port via non-optional constructor injection (OutboxRelay, ProductsController,
// ProductEventListener itself) to prove that absence fails the context fast, rather than the
// service silently booting with no adapter wired up.
class AdapterGatingTest {

    @Configuration
    static class MessagingTestConfig {
        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        ProductIndexer productIndexer() {
            return mock(ProductIndexer.class);
        }
    }

    public static class MessagingPortConsumer {
        public MessagingPortConsumer(DomainEventPublisher publisher) {
        }
    }

    private final ApplicationContextRunner messagingRunner = new ApplicationContextRunner()
            .withUserConfiguration(MessagingTestConfig.class, KafkaDomainEventPublisher.class,
                    ProductEventListener.class);

    @Test
    void kafkaIsTheSoleMessagingAdapterWhenBrokerPropertyIsAbsent() {
        messagingRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DomainEventPublisher.class);
            assertThat(context).hasSingleBean(ProductEventListener.class);
        });
    }

    @Test
    void kafkaIsTheSoleMessagingAdapterWhenBrokerIsExplicitlyKafka() {
        messagingRunner.withPropertyValues("gskart.messaging.broker=kafka").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DomainEventPublisher.class);
            assertThat(context).hasSingleBean(ProductEventListener.class);
        });
    }

    @Test
    void unimplementedBrokerValueLeavesTheMessagingPortsWithNoBean() {
        messagingRunner.withPropertyValues("gskart.messaging.broker=sns-sqs")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DomainEventPublisher.class);
                    assertThat(context).doesNotHaveBean(ProductEventListener.class);
                });
    }

    @Test
    void unimplementedBrokerValueFailsDownstreamConsumerFastInsteadOfSilentlyNoOpping() {
        messagingRunner.withUserConfiguration(MessagingPortConsumer.class)
                .withPropertyValues("gskart.messaging.broker=sns-sqs")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    static class SearchTestConfig {
        // ElasticsearchProductIndexer's @PostConstruct calls
        // elasticsearchOperations.indexOps(...).exists() - a plain mock() returns null for
        // indexOps(...), so it must be stubbed to avoid an NPE during context startup.
        @Bean
        ElasticsearchOperations elasticsearchOperations() {
            ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
            IndexOperations indexOperations = mock(IndexOperations.class);
            when(operations.indexOps(ProductDocument.class)).thenReturn(indexOperations);
            when(indexOperations.exists()).thenReturn(true);
            return operations;
        }
    }

    public static class SearchPortConsumer {
        public SearchPortConsumer(ProductIndexer productIndexer, ISearchService searchService) {
        }
    }

    private final ApplicationContextRunner searchRunner = new ApplicationContextRunner()
            .withUserConfiguration(SearchTestConfig.class, ElasticsearchProductIndexer.class,
                    ElasticsearchSearchService.class);

    @Test
    void elasticsearchIsTheSoleSearchAdapterWhenEnginePropertyIsAbsent() {
        searchRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductIndexer.class);
            assertThat(context).hasSingleBean(ISearchService.class);
        });
    }

    @Test
    void elasticsearchIsTheSoleSearchAdapterWhenEngineIsExplicitlyElasticsearch() {
        searchRunner.withPropertyValues("gskart.search.engine=elasticsearch").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductIndexer.class);
            assertThat(context).hasSingleBean(ISearchService.class);
        });
    }

    @Test
    void unimplementedEngineValueLeavesTheSearchPortsWithNoBean() {
        searchRunner.withPropertyValues("gskart.search.engine=opensearch")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ProductIndexer.class);
                    assertThat(context).doesNotHaveBean(ISearchService.class);
                });
    }

    @Test
    void unimplementedEngineValueFailsDownstreamConsumerFastInsteadOfSilentlyNoOpping() {
        searchRunner.withUserConfiguration(SearchPortConsumer.class)
                .withPropertyValues("gskart.search.engine=opensearch")
                .run(context -> assertThat(context).hasFailed());
    }
}
