package com.gskart.product.fakestore.DTOs.requests;

import lombok.Data;

@Data
public class ProductRequest {
    private String title;
    private double price;
    private String description;
    private String image;
    private String category;
}
