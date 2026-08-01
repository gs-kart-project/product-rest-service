package com.gskart.product.DTOs;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class BaseDto {
    private  Long id;
    private String createdBy;
    private OffsetDateTime createdOn;
    private  String modifiedBy;
    private OffsetDateTime modifiedOn;
}
