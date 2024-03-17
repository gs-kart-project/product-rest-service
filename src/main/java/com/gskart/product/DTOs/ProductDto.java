package com.gskart.product.DTOs;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class ProductDto {
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private String imageUrl;
    private Double price;
}
