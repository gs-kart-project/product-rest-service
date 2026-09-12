package com.gskart.product.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import com.gskart.commons.domain.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity(name = "categories")
public class Category extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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
