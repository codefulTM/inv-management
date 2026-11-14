package com.codefultm.inventory_management.common.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class ProductDtos {

    public record CreateProductRequest(
            @NotBlank String sku,
            @NotBlank String name,
            @PositiveOrZero BigDecimal costPrice,
            @Positive BigDecimal sellPrice,
            @PositiveOrZero Integer lowStockThreshold
    ) {}

    public record UpdateProductRequest(
            String name,
            BigDecimal costPrice,
            BigDecimal sellPrice,
            Integer lowStockThreshold
    ) {}

    public record ProductResponse(
            Long id,
            String sku,
            String name,
            BigDecimal costPrice,
            BigDecimal sellPrice,
            boolean active,
            Integer lowStockThreshold,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
