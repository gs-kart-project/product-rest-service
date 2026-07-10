package com.gskart.product.services;

import com.gskart.product.search.ProductDocument;
import org.springframework.data.domain.Page;

import java.util.Map;

public interface ISearchService {
    Page<ProductDocument> searchProducts(String query, int pageNo, int pageSize, Map<String, String> sortProperties);
}
