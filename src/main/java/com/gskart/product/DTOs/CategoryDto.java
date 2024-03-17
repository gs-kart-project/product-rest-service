package com.gskart.product.DTOs;

import lombok.Data;

@Data
public class CategoryDto {
    private  Long id;
    private String name;
    private String description;
    private String imageUrl;
}
