package com.gskart.product.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import com.gskart.commons.domain.BaseAuditEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity(name = "products")
// Keeps deleted products out of every query automatically. Status is stored as a number (0=ACTIVE, 1=DELETED),
// so this has to stay "<> 1" - writing 'DELETED' here gets coerced to 0 by MySQL and ends up hiding the wrong rows.
@SQLRestriction("status <> 1")
public class Product extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
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
