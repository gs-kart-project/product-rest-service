package com.gskart.product.search;

import com.gskart.product.events.ProductEvent;

// Engine-neutral write port for the index - Elasticsearch implements it today, maybe OpenSearch
// later. The message consumer only depends on this interface, not a concrete engine client.
public interface ProductIndexer {
    void upsert(ProductEvent event);
}
