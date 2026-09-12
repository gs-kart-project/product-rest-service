package com.gskart.product.search.elasticsearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.math.BigDecimal;

// ElasticsearchProductIndexer sets its own version (the event's occurredOn as epoch nanos) on
// every write, and ES rejects a write that's older than what's already indexed - that's what
// stops a stale or out-of-order delivery from overwriting newer data.
@Data
@Document(indexName = "products", versionType = Document.VersionType.EXTERNAL)
public class ProductDocument {

    // productId as a string, since ES needs a string _id. Kept separately below too, so it can be
    // used as a numeric sort field - _id values don't sort numerically.
    @Id
    private String id;

    private Long productId;

    // "name.keyword" lets sort=name work - analyzed text fields can't be sorted directly.
    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = { @InnerField(suffix = "keyword", type = FieldType.Keyword) }
    )
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    // scaled_float instead of double so money round-trips exactly instead of drifting through
    // floating point. scalingFactor=100 covers up to 2 decimal places, matching the DECIMAL(12,2)
    // column this comes from.
    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal price;

    private Long categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;

    // Mirrors the MySQL soft delete. Search always filters to ACTIVE (see
    // ElasticsearchSearchService); DELETED rows are kept, not exposed, for future admin/reporting use.
    @Field(type = FieldType.Keyword)
    private String status;

    // The event's occurredOn as epoch nanoseconds (see the class comment above) - nanos instead of
    // ES's millisecond-precision Date, so two deliveries in the same millisecond can't clobber each
    // other. Not a @Field - @Version maps to ES's own version metadata, not a value in _source.
    @Version
    private Long version;
}
