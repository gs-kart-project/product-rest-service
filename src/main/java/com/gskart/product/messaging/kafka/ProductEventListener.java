package com.gskart.product.messaging.kafka;

import com.gskart.product.events.ProductEvent;
import com.gskart.product.search.ProductIndexer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// Only active consumer adapter when gskart.messaging.broker=kafka (the local default) - a future
// sns-sqs listener depends on the same ProductIndexer port, so the handler logic never changes.
@Component
@ConditionalOnProperty(name = "gskart.messaging.broker", havingValue = "kafka", matchIfMissing = true)
public class ProductEventListener {

    private final ProductIndexer productIndexer;

    public ProductEventListener(ProductIndexer productIndexer) {
        this.productIndexer = productIndexer;
    }

    @KafkaListener(topics = "${gskart.product.events.topic}")
    public void onProductEvent(ProductEvent event) {
        productIndexer.upsert(event);
    }
}
