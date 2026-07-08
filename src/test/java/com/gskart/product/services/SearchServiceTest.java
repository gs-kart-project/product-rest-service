package com.gskart.product.services;

import com.gskart.product.entities.Product;
import com.gskart.product.respositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    private ProductRepository productRepository;
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        searchService = new SearchService(productRepository);
    }

    private Pageable capturePageable(String query, Map<String, String> sort) {
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findAllByNameContainingOrDescriptionContaining(eq(query), eq(query), org.mockito.ArgumentMatchers.any()))
                .thenReturn(page);

        searchService.searchProducts(query, 0, 10, sort);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(productRepository)
                .findAllByNameContainingOrDescriptionContaining(eq(query), eq(query), captor.capture());
        return captor.getValue();
    }

    @Test
    void buildsAscendingSortByDefault() {
        Pageable pageable = capturePageable("laptop", Map.of("name", "asc"));

        Sort.Order order = pageable.getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    @Test
    void buildsDescendingSortWhenDirectionStartsWithDesc() {
        Pageable pageable = capturePageable("phone", Map.of("price", "desc"));

        Sort.Order order = pageable.getSort().getOrderFor("price");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void unknownDirectionFallsBackToAscending() {
        Pageable pageable = capturePageable("tv", Map.of("name", "sideways"));

        Sort.Order order = pageable.getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void emptyDirectionFallsBackToAscending() {
        Pageable pageable = capturePageable("radio", java.util.Collections.singletonMap("name", ""));

        Sort.Order order = pageable.getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void returnsPageFromRepository() {
        Product product = new Product();
        product.setName("Laptop");
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findAllByNameContainingOrDescriptionContaining(eq("lap"), eq("lap"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(page);

        Page<Product> result = searchService.searchProducts("lap", 0, 10, Map.of("name", "asc"));

        assertThat(result.getContent()).containsExactly(product);
    }
}
