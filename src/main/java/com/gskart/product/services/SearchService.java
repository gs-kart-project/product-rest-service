package com.gskart.product.services;

import com.gskart.product.entities.Product;
import com.gskart.product.respositories.ProductRepository;
import com.mysql.cj.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchService implements ISearchService {

    private final ProductRepository productRepository;
    public SearchService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> searchProducts(String query, int pageNo, int pageSize, Map<String, String> sortProperties){
        List<Sort.Order> sortOrders = new ArrayList<>();

        for(Map.Entry<String, String> sortEntry : sortProperties.entrySet()){
            Sort.Direction sortDir;
            if(!StringUtils.isNullOrEmpty(sortEntry.getValue()) && sortEntry.getValue().toLowerCase().startsWith("desc")){
                sortDir = Sort.Direction.DESC;
            }
            else {
                sortDir = Sort.Direction.ASC;
            }
            Sort.Order sortOrder = new Sort.Order(sortDir, sortEntry.getKey());
            sortOrders.add(sortOrder);
        }

        Sort sort = Sort.by(sortOrders);
        Pageable pageParams = PageRequest.of(pageNo, pageSize, sort);
        Page<Product> productsPaged = productRepository.findAllByNameContainingOrDescriptionContaining(query, query , pageParams);
        return productsPaged;
    }
}
