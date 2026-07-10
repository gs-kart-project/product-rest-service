package com.gskart.product.services;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.gskart.product.search.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SearchService implements ISearchService {

    private static final String ACTIVE_STATUS = "ACTIVE";

    // Public API sort names -> the ES field actually sorted on ("name" is analyzed text, so it
    // needs its "keyword" sub-field; "id" sorts on the numeric productId, not the string _id).
    private static final Map<String, String> SORT_FIELD_MAP = Map.of(
            "name", "name.keyword",
            "price", "price",
            "id", "productId");

    private final ElasticsearchOperations elasticsearchOperations;

    public SearchService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public Page<ProductDocument> searchProducts(String query, int pageNo, int pageSize, Map<String, String> sortProperties) {
        Pageable pageable = PageRequest.of(pageNo, pageSize, buildSort(sortProperties));

        Query esQuery = Query.of(q -> q.bool(b -> b
                .must(m -> m.multiMatch(mm -> mm
                        .fields("name", "description")
                        .query(query)
                        .fuzziness("AUTO")))
                .filter(f -> f.term(t -> t.field("status").value(ACTIVE_STATUS)))));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(esQuery)
                .withPageable(pageable)
                .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
        List<ProductDocument> content = hits.getSearchHits().stream().map(SearchHit::getContent).toList();
        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }

    // No sortProperties (the "relevance" default) leaves the Pageable unsorted, so ES falls back
    // to its natural _score ordering for the scored multi_match query.
    private Sort buildSort(Map<String, String> sortProperties) {
        if (sortProperties == null || sortProperties.isEmpty()) {
            return Sort.unsorted();
        }
        Map.Entry<String, String> sortEntry = sortProperties.entrySet().iterator().next();
        String esField = SORT_FIELD_MAP.getOrDefault(sortEntry.getKey(), sortEntry.getKey());
        Sort.Direction direction = sortEntry.getValue() != null && sortEntry.getValue().toLowerCase().startsWith("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, esField);
    }
}
