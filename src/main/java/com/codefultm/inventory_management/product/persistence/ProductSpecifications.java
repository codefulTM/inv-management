package com.codefultm.inventory_management.product.persistence;

import jakarta.persistence.criteria.JoinType;

import org.springframework.data.jpa.domain.Specification;

/**
 * Composable query fragments for {@link Product}.
 *
 * <p>Each method returns one small, independent predicate. The service combines
 * them with {@code Specification.and(...)}, so any subset of filters can be applied
 * in a single SQL statement — including name <em>and</em> category at the same time.
 *
 * <p>All predicates are {@code null}-free by construction: callers pass only the
 * filters that are actually present, so no {@code :param IS NULL} guards are needed
 * (those are brittle on PostgreSQL, which cannot always infer the bind type).
 */
public final class ProductSpecifications {

    /** Escape character used with {@code LIKE} so user input cannot inject wildcards. */
    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {
        // static helper class
    }

    /** Only non-deleted products are ever visible through the API. */
    public static Specification<Product> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    /** Case-insensitive "contains" match on the product name. */
    public static Specification<Product> nameContains(String fragment) {
        String pattern = "%" + escapeLike(fragment.toLowerCase()) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE);
    }

    /**
     * Exact match on the category name.
     *
     * <p>Uses a {@code LEFT JOIN} on purpose: products without a category must not
     * disappear from a query that happens to filter on something else. An inner join
     * would silently drop them.
     */
    public static Specification<Product> inCategoryNamed(String categoryName) {
        return (root, query, cb) -> cb.equal(
                root.join("category", JoinType.LEFT).get("name"),
                categoryName
        );
    }

    /**
     * Escapes the three characters that are special inside a SQL {@code LIKE} pattern
     * so that a user typing {@code %} or {@code _} searches for them literally.
     */
    private static String escapeLike(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
