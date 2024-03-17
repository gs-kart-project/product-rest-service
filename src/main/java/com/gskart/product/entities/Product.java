package com.gskart.product.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;

// Lombok attributes
@EqualsAndHashCode(callSuper = true)
@Data
//Db Attributes
@Entity(name = "products")
public class Product extends BaseEntity {
    private String name;
    private String description;
    private String imageUrl;
    private Double price;
    private Status status;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Category category;

    public enum Status {
        ACTIVE,
        DELETED
    }
}
