package com.codefultm.inventory_management.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.codefultm.inventory_management.common.config.JpaAuditingConfig;
import com.codefultm.inventory_management.testsupport.TestcontainersConfig;

/**
 * Repository tests for {@link ProductRepository}.
 *
 * <p>Uses {@code @DataJpaTest} (loads only JPA beans) + Testcontainers
 * (real PostgreSQL) so CHECK / UNIQUE constraints are enforced by the database.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} keeps the real
 * Testcontainers datasource instead of swapping in an in-memory DB, so the
 * Flyway migration from {@code db/migration} actually runs (the DataJpaTest
 * slice includes FlywayAutoConfiguration).
 *
 * <p>Scenarios:
 * <ol>
 *   <li>Insert product → search by name / SKU</li>
 *   <li>Paging</li>
 *   <li>{@code sku} UNIQUE — insert duplicate → exception</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfig.class, JpaAuditingConfig.class })
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category electronicsCategory;
    private Category clothingCategory;

    // ---------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        // Clean slate
        productRepository.deleteAll();

        electronicsCategory = categoryRepository.save(new Category("Electronics"));
        clothingCategory = categoryRepository.save(new Category("Clothing"));
    }

    // ---------------------------------------------------------------------
    // Search tests
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Search by name / SKU")
    class SearchTests {

        @Test
        @DisplayName("findByNameContainingIgnoreCase — partial match, case-insensitive")
        void searchByName() {
            productRepository.save(new Product("ELEC-001", "Wireless Mouse", BigDecimal.valueOf(25), BigDecimal.valueOf(45)));
            productRepository.save(new Product("ELEC-002", "WIRELESS Keyboard", BigDecimal.valueOf(30), BigDecimal.valueOf(55)));
            productRepository.save(new Product("ELEC-003", "USB-C Hub", BigDecimal.valueOf(15), BigDecimal.valueOf(35)));

            Page<Product> result = productRepository.findByNameContainingIgnoreCaseAndActiveTrue("wireless", PageRequest.of(0, 10));

            assertThat(result).hasSize(2);
            assertThat(result.getContent())
                    .extracting(Product::getName)
                    .containsExactlyInAnyOrder("Wireless Mouse", "WIRELESS Keyboard");
        }

        @Test
        @DisplayName("findBySkuAndActiveTrue — exact match")
        void searchBySku() {
            productRepository.save(new Product("ELEC-001", "Wireless Mouse", BigDecimal.valueOf(25), BigDecimal.valueOf(45)));

            Optional<Product> found = productRepository.findBySkuAndActiveTrue("ELEC-001");

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Wireless Mouse");
        }

        @Test
        @DisplayName("findBySkuAndActiveTrue — returns empty when SKU not found")
        void searchBySkuNotFound() {
            Optional<Product> found = productRepository.findBySkuAndActiveTrue("NONEXISTENT");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("findByCategoryNameAndActiveTrue — filter by category")
        void searchByCategory() {
            Product p1 = productRepository.save(new Product("ELEC-001", "Mouse", BigDecimal.valueOf(25), BigDecimal.valueOf(45)));
            p1.setCategory(electronicsCategory);
            productRepository.save(p1);

            Product p2 = productRepository.save(new Product("CLTH-001", "T-Shirt", BigDecimal.valueOf(10), BigDecimal.valueOf(20)));
            p2.setCategory(clothingCategory);
            productRepository.save(p2);

            Page<Product> result = productRepository.findByCategoryNameAndActiveTrue("Electronics", PageRequest.of(0, 10));

            assertThat(result).hasSize(1);
            assertThat(result.getContent().get(0).getSku()).isEqualTo("ELEC-001");
        }

        @Test
        @DisplayName("findByActiveTrue — only active products")
        void onlyActiveProducts() {
            Product active = productRepository.save(new Product("ELEC-001", "Active Product", BigDecimal.valueOf(10), BigDecimal.valueOf(20)));
            Product inactive = productRepository.save(new Product("ELEC-002", "Inactive Product", BigDecimal.valueOf(10), BigDecimal.valueOf(20)));
            inactive.setActive(false);
            productRepository.save(inactive);

            Page<Product> result = productRepository.findByActiveTrue(PageRequest.of(0, 10));

            assertThat(result).hasSize(1);
            assertThat(result.getContent().get(0).getSku()).isEqualTo("ELEC-001");
        }
    }

    // ---------------------------------------------------------------------
    // Combined filter tests (Specification)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Specification search — filters combine")
    class SpecificationTests {

        @BeforeEach
        void seed() {
            // both name AND category match
            save("ELEC-001", "Wireless Mouse", electronicsCategory);
            save("ELEC-002", "WIRELESS Keyboard", electronicsCategory);
            // name matches but category does NOT
            save("CLTH-001", "Wireless T-Shirt", clothingCategory);
            // no category at all — must survive a name-only filter (LEFT JOIN)
            save("MISC-001", "Wireless Adapter", null);
            // inactive product with matching name + category — must stay hidden
            Product inactive = save("ELEC-003", "Wireless Old Mouse", electronicsCategory);
            inactive.setActive(false);
            productRepository.save(inactive);
        }

        @Test
        @DisplayName("name only matches across all categories and uncategorised products")
        void nameOnly() {
            Page<Product> result = productRepository.findAll(
                    ProductSpecifications.active().and(ProductSpecifications.nameContains("wireless")),
                    PageRequest.of(0, 10));

            // ELEC-001, ELEC-002, CLTH-001, MISC-001 — inactive ELEC-003 excluded
            assertThat(result.getContent())
                    .extracting(Product::getSku)
                    .containsExactlyInAnyOrder("ELEC-001", "ELEC-002", "CLTH-001", "MISC-001");
        }

        @Test
        @DisplayName("category only returns every product in that category")
        void categoryOnly() {
            Page<Product> result = productRepository.findAll(
                    ProductSpecifications.active().and(ProductSpecifications.inCategoryNamed("Electronics")),
                    PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(Product::getSku)
                    .containsExactlyInAnyOrder("ELEC-001", "ELEC-002");
        }

        @Test
        @DisplayName("name AND category intersect — the clothing match is excluded")
        void nameAndCategory() {
            Page<Product> result = productRepository.findAll(
                    ProductSpecifications.active()
                            .and(ProductSpecifications.nameContains("wireless"))
                            .and(ProductSpecifications.inCategoryNamed("Electronics")),
                    PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(Product::getSku)
                    .containsExactlyInAnyOrder("ELEC-001", "ELEC-002");
        }

        @Test
        @DisplayName("name filter is case-insensitive")
        void caseInsensitive() {
            Page<Product> result = productRepository.findAll(
                    ProductSpecifications.active()
                            .and(ProductSpecifications.nameContains("WIRELESS"))
                            .and(ProductSpecifications.inCategoryNamed("Clothing")),
                    PageRequest.of(0, 10));

            assertThat(result.getContent())
                    .extracting(Product::getSku)
                    .containsExactly("CLTH-001");
        }

        @Test
        @DisplayName("LIKE wildcards in user input are escaped, not interpreted")
        void wildcardsAreEscaped() {
            // "%" as a search term must match nothing (no product name contains a literal %)
            Page<Product> result = productRepository.findAll(
                    ProductSpecifications.active().and(ProductSpecifications.nameContains("%")),
                    PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
        }

        private Product save(String sku, String name, Category category) {
            Product product = new Product(sku, name, BigDecimal.valueOf(10), BigDecimal.valueOf(20));
            product.setCategory(category);
            return productRepository.save(product);
        }
    }

    // ---------------------------------------------------------------------
    // Paging tests
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Paging")
    class PagingTests {

        @Test
        @DisplayName("page 0 returns first 3 products")
        void page0() {
            for (int i = 1; i <= 5; i++) {
                productRepository.save(new Product("PAGING-" + i, "Product " + i, BigDecimal.valueOf(i), BigDecimal.valueOf(i * 2)));
            }

            Page<Product> page0 = productRepository.findByActiveTrue(PageRequest.of(0, 3));

            assertThat(page0.getContent()).hasSize(3);
            assertThat(page0.getTotalElements()).isEqualTo(5);
            assertThat(page0.getTotalPages()).isEqualTo(2);
            assertThat(page0.getNumber()).isEqualTo(0);
            assertThat(page0.hasContent()).isTrue();
        }

        @Test
        @DisplayName("page 1 returns remaining products")
        void page1() {
            for (int i = 1; i <= 5; i++) {
                productRepository.save(new Product("PAGING-" + i, "Product " + i, BigDecimal.valueOf(i), BigDecimal.valueOf(i * 2)));
            }

            Page<Product> page1 = productRepository.findByActiveTrue(PageRequest.of(1, 3));

            assertThat(page1.getContent()).hasSize(2);
            assertThat(page1.getTotalElements()).isEqualTo(5);
            assertThat(page1.getNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("page beyond last returns empty content but real total count")
        void emptyPage() {
            for (int i = 1; i <= 5; i++) {
                productRepository.save(new Product("PAGING-" + i, "Product " + i, BigDecimal.valueOf(i), BigDecimal.valueOf(i * 2)));
            }

            // 5 products with page size 3 => 2 pages (0 and 1); page 10 is far beyond
            Page<Product> page = productRepository.findByActiveTrue(PageRequest.of(10, 3));

            assertThat(page.getContent()).isEmpty();
            // totalElements still reflects ALL matching rows, not the current page
            assertThat(page.getTotalElements()).isEqualTo(5L);
            assertThat(page.getTotalPages()).isEqualTo(2);
            assertThat(page.getNumber()).isEqualTo(10);
            assertThat(page.hasNext()).isFalse();
            assertThat(page.hasPrevious()).isTrue();
        }
    }

    // ---------------------------------------------------------------------
    // Uniqueness tests
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("SKU UNIQUE constraint")
    class UniquenessTests {

        @Test
        @DisplayName("insert duplicate SKU throws DataIntegrityViolationException")
        void duplicateSkuThrowsException() {
            productRepository.saveAndFlush(
                    new Product("UNIQUE-001", "First Product", BigDecimal.valueOf(10), BigDecimal.valueOf(20)));

            assertThatThrownBy(() -> productRepository.saveAndFlush(
                    new Product("UNIQUE-001", "Duplicate Product", BigDecimal.valueOf(15), BigDecimal.valueOf(30))))
                    .isInstanceOfAny(
                            DataIntegrityViolationException.class,
                            PersistenceException.class
                    );
        }

        @Test
        @DisplayName("different SKUs are allowed")
        void differentSkusAllowed() {
            productRepository.save(new Product("DIFF-001", "Product A", BigDecimal.valueOf(10), BigDecimal.valueOf(20)));
            productRepository.save(new Product("DIFF-002", "Product B", BigDecimal.valueOf(15), BigDecimal.valueOf(30)));

            assertThat(productRepository.count()).isEqualTo(2);
        }
    }
}
