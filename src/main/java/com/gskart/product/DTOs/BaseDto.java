package com.gskart.product.DTOs;

import lombok.Data;

import java.util.Date;

@Data
public class BaseDto {
    private  Long id;
    private String createdBy;
    private Date createdOn;
    private  String modifiedBy;
    private Date modifiedOn;
}
