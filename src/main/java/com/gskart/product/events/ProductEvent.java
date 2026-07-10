package com.gskart.product.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

// Denormalized snapshot of a product at the time of the write. The indexer never reads back from
// MySQL, so this payload is the only thing it has to work with - and occurredOn doubles as the
// external version used to drop stale/reordered deliveries (see ProductIndexer).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEvent {
    private String eventId;
    private Long productId;
    private Instant occurredOn;
    private String status;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
}
