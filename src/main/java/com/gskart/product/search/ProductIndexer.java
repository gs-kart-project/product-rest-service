package com.gskart.product.search;

import com.gskart.product.events.ProductEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.data.elasticsearch.VersionConflictException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProductIndexer {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexer.class);

    private final ElasticsearchOperations elasticsearchOperations;

    public ProductIndexer(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    // Without this, the index only comes into existence via ES's dynamic mapping on the first
    // save() - which ignores the @Field annotations on ProductDocument (e.g. status would end up
    // as analyzed "text" instead of "keyword", silently breaking the exact-match ACTIVE filter).
    // Once this runs at startup the index always exists before any write, so getExisting()'s
    // NoSuchIndexException catch below is purely defensive (e.g. someone deleted the index by hand).
    @PostConstruct
    void ensureIndexExists() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        if (!indexOps.exists()) {
            indexOps.create();
            indexOps.putMapping();
        }
    }

    // Always upserts - a deleted product still lands here, just with status=DELETED (soft delete
    // in the index, mirroring the MySQL soft delete). Guards against stale/reordered events twice:
    // a cheap pre-check here (avoids a doomed network round trip for the common case), and ES's own
    // external-version enforcement on save() (the authoritative guard - catches a stale write that
    // races past the pre-check, e.g. two deliveries landing concurrently).
    public void upsert(ProductEvent event) {
        String documentId = event.getProductId().toString();
        long externalVersion = toExternalVersion(event.getOccurredOn());
        ProductDocument existing = getExisting(documentId);
        if (existing != null && existing.getVersion() != null && externalVersion <= existing.getVersion()) {
            return;
        }
        try {
            elasticsearchOperations.save(toDocument(event, externalVersion));
        } catch (VersionConflictException staleOrDuplicateDelivery) {
            log.debug("Dropped stale/reordered event for product {} (version {})", event.getProductId(), externalVersion);
        }
    }

    // The "products" index doesn't exist until the first successful save() (ES auto-creates it on
    // write); a .get() before that point throws NoSuchIndexException rather than returning null.
    private ProductDocument getExisting(String documentId) {
        try {
            return elasticsearchOperations.get(documentId, ProductDocument.class);
        } catch (NoSuchIndexException e) {
            return null;
        }
    }

    // Epoch nanoseconds: full timestamp precision as ES's external document version (see
    // ProductDocument's @Version field), vs. the millisecond precision an ES Date field would give.
    private long toExternalVersion(Instant occurredOn) {
        return occurredOn.getEpochSecond() * 1_000_000_000L + occurredOn.getNano();
    }

    private ProductDocument toDocument(ProductEvent event, long externalVersion) {
        ProductDocument document = new ProductDocument();
        document.setId(event.getProductId().toString());
        document.setProductId(event.getProductId());
        document.setName(event.getName());
        document.setDescription(event.getDescription());
        document.setPrice(event.getPrice());
        document.setImageUrl(event.getImageUrl());
        document.setCategoryId(event.getCategoryId());
        document.setCategoryName(event.getCategoryName());
        document.setStatus(event.getStatus());
        document.setVersion(externalVersion);
        return document;
    }
}
