package com.codefultm.inventory_management.product.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for Category entity.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
