package com.gskart.product.services;

import com.gskart.product.entities.Product;
import org.springframework.data.domain.Page;

import java.util.Map;

public interface ISearchService {
    Page<Product> searchProducts(String query, int pageNo, int pageSize, Map<String, String> sortProperties);
}
