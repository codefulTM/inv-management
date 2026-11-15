package com.codefultm.inventory_management.product;

import org.springframework.stereotype.Component;

import com.codefultm.inventory_management.common.dto.ProductDtos.CreateProductRequest;
import com.codefultm.inventory_management.common.dto.ProductDtos.ProductResponse;
import com.codefultm.inventory_management.common.dto.ProductDtos.UpdateProductRequest;
import com.codefultm.inventory_management.product.persistence.Product;

/**
 * Converts between the {@link Product} entity and the API DTO records.
 *
 * <p>Lives inside the {@code product} slice: it is the only place allowed to know
 * both the entity and the API contract, which keeps {@code persistence/} free of
 * any DTO dependency.
 */
@Component
public class ProductMapper {

    /** Default low-stock threshold applied when the request does not specify one. */
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;

    /**
     * Builds a new {@code Product} from a create request.
     * The entity is always created as active.
     */
    public Product toEntity(CreateProductRequest request) {
        Product product = new Product(
                request.sku(),
                request.name(),
                request.costPrice(),
                request.sellPrice()
        );
        product.setLowStockThreshold(
                request.lowStockThreshold() != null
                        ? request.lowStockThreshold()
                        : DEFAULT_LOW_STOCK_THRESHOLD
        );
        return product;
    }

    /**
     * Applies a partial update to an existing entity.
     *
     * <p>Only non-null fields are copied: a {@code PUT} with a {@code null} field
     * means "leave this value unchanged" rather than "erase it".
     */
    public void updateEntity(Product product, UpdateProductRequest request) {
        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.costPrice() != null) {
            product.setCostPrice(request.costPrice());
        }
        if (request.sellPrice() != null) {
            product.setSellPrice(request.sellPrice());
        }
        if (request.lowStockThreshold() != null) {
            product.setLowStockThreshold(request.lowStockThreshold());
        }
    }

    /** Projects an entity onto the API response shape. */
    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCostPrice(),
                product.getSellPrice(),
                product.isActive(),
                product.getLowStockThreshold(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
