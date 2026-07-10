package com.gskart.product.search;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.math.BigDecimal;

// versionType = EXTERNAL: ProductIndexer supplies its own monotonic version (the source event's
// occurredOn, as epoch nanos) on every write; ES then atomically rejects (VersionConflictException)
// any write whose version isn't strictly greater than what's currently indexed, so stale/reordered
// deliveries are dropped by ES itself rather than relying solely on the indexer's own
// read-then-write pre-check (m5 fix).
@Data
@Document(indexName = "products", versionType = Document.VersionType.EXTERNAL)
public class ProductDocument {

    // ES document id (= productId as a string); productId is kept separately (below) so it can be
    // used as a numeric sort field, since text/keyword _id values don't sort numerically.
    @Id
    private String id;

    private Long productId;

    // "name.keyword" sub-field lets sort=name work (fielddata is disabled on analyzed text fields).
    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = { @InnerField(suffix = "keyword", type = FieldType.Keyword) }
    )
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    // scaled_float (not double) so money round-trips exactly rather than through IEEE-754 binary
    // floating point (m6 fix); scalingFactor=100 supports up to 2 decimal places, matching the
    // DECIMAL(12,2) column this is sourced from.
    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal price;

    private Long categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;

    // ACTIVE / DELETED - soft delete in the index mirrors the MySQL soft delete; search always
    // filters to ACTIVE (see SearchService). Retained (not exposed) for future admin/reporting use.
    @Field(type = FieldType.Keyword)
    private String status;

    // ES's external document version (see the class-level @Document comment): the source
    // ProductEvent's occurredOn as epoch nanoseconds, giving full timestamp precision (an ES `Date`
    // field is only millisecond-precision, which could let two same-millisecond, out-of-order
    // deliveries clobber each other). Not a @Field - @Version fields aren't part of _source, they
    // map to ES's version metadata (populated from _version on read, sent as ?version=&version_type=
    // external on write).
    @Version
    private Long version;
}
