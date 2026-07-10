package com.gskart.product.messaging.kafka;

import com.gskart.product.events.ProductEvent;
import com.gskart.product.search.ProductIndexer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
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
