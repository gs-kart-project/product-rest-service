package com.gskart.product.search.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import com.gskart.product.search.ProductSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticsearchSearchServiceTest {

    private ElasticsearchOperations elasticsearchOperations;
    private ElasticsearchSearchService searchService;

    @BeforeEach
    void setUp() {
        elasticsearchOperations = mock(ElasticsearchOperations.class);
        searchService = new ElasticsearchSearchService(elasticsearchOperations);
    }

    @SuppressWarnings("unchecked")
    private SearchHits<ProductDocument> emptyHits() {
        SearchHits<ProductDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(hits.getTotalHits()).thenReturn(0L);
        return hits;
    }

    private NativeQuery capturedQuery(String query, int page, int size, Map<String, String> sort) {
        SearchHits<ProductDocument> hits = emptyHits();
        when(elasticsearchOperations.search(any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class))).thenReturn(hits);

        searchService.searchProducts(query, page, size, sort);

        ArgumentCaptor<org.springframework.data.elasticsearch.core.query.Query> captor =
                ArgumentCaptor.forClass(org.springframework.data.elasticsearch.core.query.Query.class);
        verify(elasticsearchOperations).search(captor.capture(), eq(ProductDocument.class));
        return (NativeQuery) captor.getValue();
    }

    @Test
    void searchAlwaysFiltersToActiveStatus() {
        NativeQuery nativeQuery = capturedQuery("laptop", 0, 10, Map.of());

        Query esQuery = nativeQuery.getQuery();
        assertThat(esQuery.isBool()).isTrue();
        BoolQuery boolQuery = esQuery.bool();
        assertThat(boolQuery.filter()).hasSize(1);
        TermQuery statusFilter = boolQuery.filter().get(0).term();
        assertThat(statusFilter.field()).isEqualTo("status");
        assertThat(statusFilter.value().stringValue()).isEqualTo("ACTIVE");
    }

    @Test
    void searchUsesMultiMatchWithFuzzinessOnNameAndDescription() {
        NativeQuery nativeQuery = capturedQuery("laptop", 0, 10, Map.of());

        BoolQuery boolQuery = nativeQuery.getQuery().bool();
        assertThat(boolQuery.must()).hasSize(1);
        MultiMatchQuery multiMatch = boolQuery.must().get(0).multiMatch();
        assertThat(multiMatch.fields()).containsExactlyInAnyOrder("name", "description");
        assertThat(multiMatch.query()).isEqualTo("laptop");
        assertThat(multiMatch.fuzziness()).isEqualTo("AUTO");
    }

    @Test
    void relevanceDefaultLeavesSortUnspecified() {
        NativeQuery nativeQuery = capturedQuery("laptop", 0, 10, Map.of());

        assertThat(nativeQuery.getPageable().getSort().isUnsorted()).isTrue();
    }

    @Test
    void nameSortMapsToKeywordSubfield() {
        NativeQuery nativeQuery = capturedQuery("laptop", 0, 10, Map.of("name", "asc"));

        Pageable pageable = nativeQuery.getPageable();
        Sort.Order order = pageable.getSort().getOrderFor("name.keyword");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void priceSortDescending() {
        NativeQuery nativeQuery = capturedQuery("laptop", 0, 10, Map.of("price", "desc"));

        Sort.Order order = nativeQuery.getPageable().getSort().getOrderFor("price");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void idSortMapsToProductIdField() {
        NativeQuery nativeQuery = capturedQuery("laptop", 0, 10, Map.of("id", "asc"));

        Sort.Order order = nativeQuery.getPageable().getSort().getOrderFor("productId");
        assertThat(order).isNotNull();
    }

    @Test
    void pageNumberAndSizeAreApplied() {
        NativeQuery nativeQuery = capturedQuery("laptop", 2, 25, Map.of());

        assertThat(nativeQuery.getPageable().getPageNumber()).isEqualTo(2);
        assertThat(nativeQuery.getPageable().getPageSize()).isEqualTo(25);
    }

    @Test
    void returnsMappedPageFromSearchHitsAsEngineNeutralResult() {
        ProductDocument document = new ProductDocument();
        document.setProductId(1L);
        document.setName("Laptop");
        document.setDescription("desc");
        document.setPrice(new BigDecimal("999.99"));
        document.setImageUrl("http://img/1");
        document.setCategoryId(3L);
        document.setCategoryName("Electronics");

        SearchHit<ProductDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(document);

        SearchHits<ProductDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(hits.getTotalHits()).thenReturn(1L);
        when(elasticsearchOperations.search(any(org.springframework.data.elasticsearch.core.query.Query.class),
                eq(ProductDocument.class))).thenReturn(hits);

        Page<ProductSearchResult> result = searchService.searchProducts("lap", 0, 10, Map.of());

        assertThat(result.getTotalElements()).isEqualTo(1L);
        ProductSearchResult mapped = result.getContent().get(0);
        assertThat(mapped.getProductId()).isEqualTo(1L);
        assertThat(mapped.getName()).isEqualTo("Laptop");
        assertThat(mapped.getDescription()).isEqualTo("desc");
        assertThat(mapped.getPrice()).isEqualTo(new BigDecimal("999.99"));
        assertThat(mapped.getImageUrl()).isEqualTo("http://img/1");
        assertThat(mapped.getCategoryId()).isEqualTo(3L);
        assertThat(mapped.getCategoryName()).isEqualTo("Electronics");
    }
}
