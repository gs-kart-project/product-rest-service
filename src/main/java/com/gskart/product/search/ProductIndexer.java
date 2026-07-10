package com.gskart.product.search;

import com.gskart.product.events.ProductEvent;

// Search-engine-neutral write port for the index (ADR-D5 style seam) - implemented per engine
// (e.g. ElasticsearchProductIndexer, later OpenSearchProductIndexer). The message consumer only
// ever depends on this interface, never on a concrete engine client.
public interface ProductIndexer {
    void upsert(ProductEvent event);
}
