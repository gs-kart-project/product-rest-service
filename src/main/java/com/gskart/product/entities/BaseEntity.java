package com.gskart.product.entities;

import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
// Lombok attributes
@Data
@NoArgsConstructor

//Db Attributes
// Audit-only base: the id is declared by each concrete entity so it owns its own generation
// strategy and any extra fields.
@MappedSuperclass
public class BaseEntity {
    private String createdBy;
    private OffsetDateTime createdOn;
    private  String modifiedBy;
    private OffsetDateTime modifiedOn;
}
