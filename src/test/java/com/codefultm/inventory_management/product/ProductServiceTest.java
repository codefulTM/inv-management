package com.codefultm.inventory_management.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.codefultm.inventory_management.common.dto.ProductDtos.CreateProductRequest;
import com.codefultm.inventory_management.common.dto.ProductDtos.ProductResponse;
import com.codefultm.inventory_management.common.dto.ProductDtos.UpdateProductRequest;
import com.codefultm.inventory_management.common.error.ConflictException;
import com.codefultm.inventory_management.common.error.NotFoundException;
import com.codefultm.inventory_management.common.paging.PageResponse;
import com.codefultm.inventory_management.product.persistence.Product;
import com.codefultm.inventory_management.product.persistence.ProductRepository;

/**
 * Pure unit tests for {@link ProductService} — no Spring context, no database.
 *
 * <p>The repository is mocked with Mockito, so these tests run in milliseconds and
 * focus purely on the business rules.
 * <p>Database behaviours (UNIQUE constraint, CHECK constraint, paging SQL) are
 * covered separately by {@code ProductRepositoryTest}.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private final ProductMapper productMapper = new ProductMapper();

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, productMapper);
    }

    // ---------------------------------------------------------------------
    // create — the SKU uniqueness rule lives here
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("duplicate SKU throws ConflictException and never saves")
        void duplicateSkuThrowsConflict() {
            when(productRepository.existsBySku("ELEC-001")).thenReturn(true);

            CreateProductRequest request = new CreateProductRequest(
                    "ELEC-001", "Wireless Mouse", BigDecimal.valueOf(25), BigDecimal.valueOf(45), 5);

            assertThatThrownBy(() -> productService.create(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("ELEC-001");

            // ensure no save happens on duplicate SKU
            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("unique SKU is persisted and mapped back to a response")
        void createsProduct() {
            when(productRepository.existsBySku("ELEC-001")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product toSave = invocation.getArgument(0);
                toSave.setId(42L);
                return toSave;
            });

            CreateProductRequest request = new CreateProductRequest(
                    "ELEC-001", "Wireless Mouse", BigDecimal.valueOf(25), BigDecimal.valueOf(45), 3);

            ProductResponse response = productService.create(request);

            assertThat(response.id()).isEqualTo(42L);
            assertThat(response.sku()).isEqualTo("ELEC-001");
            assertThat(response.name()).isEqualTo("Wireless Mouse");
            assertThat(response.costPrice()).isEqualByComparingTo("25");
            assertThat(response.sellPrice()).isEqualByComparingTo("45");
            assertThat(response.lowStockThreshold()).isEqualTo(3);
            assertThat(response.active()).isTrue();
        }

        @Test
        @DisplayName("missing lowStockThreshold falls back to the default of 10")
        void appliesDefaultLowStockThreshold() {
            when(productRepository.existsBySku("ELEC-002")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductResponse response = productService.create(new CreateProductRequest(
                    "ELEC-002", "Keyboard", BigDecimal.valueOf(10), BigDecimal.valueOf(20), null));

            assertThat(response.lowStockThreshold()).isEqualTo(10);
        }
    }

    // ---------------------------------------------------------------------
    // update
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("applies only non-null fields")
        void appliesPartialUpdate() {
            Product existing = product(7L, "ELEC-001", "Old name", 25, 45, true);
            when(productRepository.findById(7L)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductResponse response = productService.update(7L,
                    new UpdateProductRequest("New name", null, BigDecimal.valueOf(99), null));

            assertThat(response.name()).isEqualTo("New name");
            assertThat(response.sellPrice()).isEqualByComparingTo("99");
            // untouched fields keep their previous values
            assertThat(response.costPrice()).isEqualByComparingTo("25");
            assertThat(response.lowStockThreshold()).isEqualTo(10);
        }

        @Test
        @DisplayName("unknown id throws NotFoundException")
        void missingProductThrowsNotFound() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(999L, new UpdateProductRequest("X", null, null, null)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("999");

            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("soft-deleted product is invisible, so update throws NotFoundException")
        void inactiveProductThrowsNotFound() {
            when(productRepository.findById(8L)).thenReturn(Optional.of(product(8L, "ELEC-009", "Gone", 1, 2, false)));

            assertThatThrownBy(() -> productService.update(8L, new UpdateProductRequest("X", null, null, null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------
    // deactivate (soft delete)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("deactivate")
    class DeactivateTests {

        @Test
        @DisplayName("flips active to false instead of deleting the row")
        void setsActiveFalse() {
            Product existing = product(5L, "ELEC-005", "Mouse", 10, 20, true);
            when(productRepository.findById(5L)).thenReturn(Optional.of(existing));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

            productService.deactivate(5L);

            assertThat(existing.isActive()).isFalse();
            verify(productRepository).save(existing);
            verify(productRepository, never()).deleteById(anyLong());
            verify(productRepository, never()).delete(any(Product.class));
        }

        @Test
        @DisplayName("already deleted product throws NotFoundException")
        void alreadyDeletedThrowsNotFound() {
            when(productRepository.findById(6L)).thenReturn(Optional.of(product(6L, "ELEC-006", "Gone", 1, 2, false)));

            assertThatThrownBy(() -> productService.deactivate(6L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ---------------------------------------------------------------------
    // reads
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("reads")
    class ReadTests {

        @Test
        @DisplayName("getById maps the entity")
        void getById() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "ELEC-001", "Mouse", 10, 20, true)));

            ProductResponse response = productService.getById(1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.sku()).isEqualTo("ELEC-001");
        }

        @Test
        @DisplayName("getById on unknown id throws NotFoundException")
        void getByIdNotFound() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getById(404L))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("getBySku returns the matching product")
        void getBySku() {
            when(productRepository.findBySkuAndActiveTrue("ELEC-001"))
                    .thenReturn(Optional.of(product(1L, "ELEC-001", "Mouse", 10, 20, true)));

            assertThat(productService.getBySku("ELEC-001").sku()).isEqualTo("ELEC-001");
        }

        @Test
        @DisplayName("getBySku on unknown SKU throws NotFoundException")
        void getBySkuNotFound() {
            when(productRepository.findBySkuAndActiveTrue("NOPE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getBySku("NOPE"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("NOPE");
        }

        @Test
        @DisplayName("search without filters queries all active products via one specification")
        void searchWithoutFilters() {
            Pageable pageable = PageRequest.of(0, 20);
            when(productRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(product(1L, "A-1", "A", 1, 2, true)), pageable, 1));

            PageResponse<ProductResponse> response = productService.search(null, null, pageable);

            assertThat(response.content()).hasSize(1);
            assertThat(response.totalElements()).isEqualTo(1);
            verify(productRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("search by name preserves paging metadata")
        void searchByName() {
            Pageable pageable = PageRequest.of(1, 3);
            when(productRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(
                            List.of(product(1L, "A-1", "Mouse", 1, 2, true),
                                    product(2L, "A-2", "Mousepad", 1, 2, true)),
                            pageable,
                            8));

            PageResponse<ProductResponse> response = productService.search("mou", null, pageable);

            assertThat(response.content()).hasSize(2);
            assertThat(response.page()).isEqualTo(1);
            assertThat(response.size()).isEqualTo(3);
            assertThat(response.totalElements()).isEqualTo(8);
        }

        @Test
        @DisplayName("search by category uses the same single query")
        void searchByCategory() {
            Pageable pageable = PageRequest.of(0, 10);
            when(productRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(product(1L, "A-1", "Mouse", 1, 2, true)), pageable, 1));

            PageResponse<ProductResponse> response = productService.search(null, "Electronics", pageable);

            assertThat(response.content()).hasSize(1);
        }

        @Test
        @DisplayName("name AND category are combined, not short-circuited")
        void searchByNameAndCategory() {
            Pageable pageable = PageRequest.of(0, 10);
            when(productRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(product(1L, "A-1", "Mouse", 1, 2, true)), pageable, 1));

            PageResponse<ProductResponse> response = productService.search("mou", "Electronics", pageable);

            assertThat(response.content()).hasSize(1);
            // still exactly one query: the old code would have run only the name branch
            verify(productRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("blank filters are treated as absent")
        void blankFiltersAreIgnored() {
            Pageable pageable = PageRequest.of(0, 10);
            when(productRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(), pageable, 0));

            productService.search("  ", "   ", pageable);

            verify(productRepository).findAll(any(Specification.class), eq(pageable));
        }
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static Product product(Long id, String sku, String name,
                                   double costPrice, double sellPrice, boolean active) {
        Product product = new Product(sku, name, BigDecimal.valueOf(costPrice), BigDecimal.valueOf(sellPrice));
        product.setId(id);
        product.setActive(active);
        return product;
    }
}
