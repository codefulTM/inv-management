package com.codefultm.inventory_management.product.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Repository for Product entity.
 * Provides CRUD operations plus search/filter capabilities.
 *
 * <p>Two styles coexist on purpose:
 * <ul>
 *   <li><b>derived queries</b> ({@code findBy...}) — readable, perfect for single-filter lookups;</li>
 *   <li><b>{@link JpaSpecificationExecutor}</b> — lets the service compose <em>several</em>
 *       optional filters in one query (see {@link ProductSpecifications}). A derived
 *       method cannot express "name OR category OR both", it would need one method per
 *       combination.</li>
 * </ul>
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Find active products by name (case-insensitive partial match).
     * Auto-generates: WHERE LOWER(name) LIKE LOWER('%?%') AND active = true ORDER BY name
     */
    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(String name, Pageable pageable);

    /**
     * Find active product by SKU (exact match).
     * Auto-generates: WHERE sku = ? AND active = true
     */
    Optional<Product> findBySkuAndActiveTrue(String sku);

    /**
     * Find active products by category name.
     * Auto-generates: WHERE category.name = ? AND active = true
     */
    Page<Product> findByCategoryNameAndActiveTrue(String categoryName, Pageable pageable);

    /**
     * Find all active products.
     * Auto-generates: WHERE active = true
     */
    Page<Product> findByActiveTrue(Pageable pageable);

    /**
     * Check if a SKU already exists (for uniqueness validation).
     */
    boolean existsBySku(String sku);
}
