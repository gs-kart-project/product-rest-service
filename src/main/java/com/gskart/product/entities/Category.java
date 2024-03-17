package com.gskart.product.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

// Lombok attributes
@EqualsAndHashCode(callSuper = true)
@Data
//Db Attributes
@Entity(name = "categories")
public class Category extends BaseEntity {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Status status;

    @OneToMany(mappedBy = "category",cascade = CascadeType.MERGE)
    List<Product> products;

    public enum Status {
        ACTIVE,
        INACTIVE,
        DELETED
    }
}
