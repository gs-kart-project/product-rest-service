package com.gskart.product.fakestore.DTOs.requests;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {
    private String title;
    private BigDecimal price;
    private String description;
    private String image;
    private String category;
}
