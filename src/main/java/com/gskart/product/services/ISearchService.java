package com.gskart.product.services;

import com.gskart.product.search.ProductSearchResult;
import org.springframework.data.domain.Page;

import java.util.Map;

public interface ISearchService {
    Page<ProductSearchResult> searchProducts(String query, int pageNo, int pageSize, Map<String, String> sortProperties);
}
