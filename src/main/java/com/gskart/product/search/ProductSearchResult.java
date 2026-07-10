package com.gskart.product.search;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Engine-neutral search hit returned by ISearchService - no Elasticsearch/OpenSearch types, so
// callers (ProductsController, ProductMapper) never depend on which engine is active.
@Data
@NoArgsConstructor
public class ProductSearchResult {
    private Long productId;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
}
