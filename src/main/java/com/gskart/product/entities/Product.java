package com.gskart.product.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

// Lombok attributes
@EqualsAndHashCode(callSuper = true)
@Data
//Db Attributes
@Entity(name = "products")
// Soft-deleted rows are excluded from every read (findById, findAll, findAllByCategoryId,
// search) so a deleted product never surfaces through the API. `status` is persisted as the
// enum ORDINAL (tinyint), so this must reference the ordinal of DELETED (Status: ACTIVE=0,
// DELETED=1) — a string literal 'DELETED' would be coerced to 0 by MySQL and invert the filter.
@SQLRestriction("status <> 1")
public class Product extends BaseEntity {
    private String name;
    private String description;
    private String imageUrl;
    @Column(precision = 12, scale = 2)
    private BigDecimal price;
    private Status status;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Category category;

    public enum Status {
        ACTIVE,
        DELETED
    }
}
