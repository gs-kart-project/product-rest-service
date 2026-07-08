package com.gskart.product.DTOs;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
public class ProductDto {
    private Long id;
    private Long categoryId;

    @NotBlank
    private String name;

    private String description;
    private String imageUrl;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;
}
