package com.codefultm.inventory_management.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codefultm.inventory_management.common.dto.ProductDtos.CreateProductRequest;
import com.codefultm.inventory_management.common.dto.ProductDtos.ProductResponse;
import com.codefultm.inventory_management.common.dto.ProductDtos.UpdateProductRequest;
import com.codefultm.inventory_management.common.error.ConflictException;
import com.codefultm.inventory_management.common.error.NotFoundException;
import com.codefultm.inventory_management.common.paging.PageRequestFactory;
import com.codefultm.inventory_management.common.paging.PageResponse;
import com.codefultm.inventory_management.product.persistence.Product;

/**
 * Web-layer tests for {@link ProductController}.
 *
 * <p>{@code @WebMvcTest} boots only the MVC slice — controller + {@code @RestControllerAdvice} —
 * with {@link ProductService} replaced by a Mockito mock, so these tests assert the
 * <b>HTTP contract</b>: status code, headers, JSON shape, and the arguments handed to the service.
 *
 * <p>{@code addFilters = false} is deliberate: JWT security arrives in Phase 4, and the role matrix
 * will then be tested against a real {@code SecurityFilterChain} instead of the default auto-configured one.
 */
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static ProductResponse sampleProduct() {
        Instant now = Instant.parse("2026-01-15T10:15:30Z");
        return new ProductResponse(1L, "ELEC-001", "Wireless Mouse",
                BigDecimal.valueOf(25), BigDecimal.valueOf(45), true, 5, now, now);
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/products
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/products")
    class ListTests {

        @Test
        @DisplayName("returns 200 with the paged envelope")
        void returnsPage() throws Exception {
            // Mock the service to return a single sample product in a paged response
            when(productService.search(isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(new PageResponse<>(List.of(sampleProduct()), 0, 20, 1));

            // Perform a GET request without any query parameters
            mockMvc.perform(get("/api/v1/products"))
                    // Assert HTTP 200 OK status
                    .andExpect(status().isOk())
                    // Assert the paging envelope fields
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    // Assert the first element in the content array
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].sku").value("ELEC-001"))
                    .andExpect(jsonPath("$.content[0].costPrice").value(25))
                    .andExpect(jsonPath("$.content[0].active").value(true));
        }

        @Test
        @DisplayName("forwards name/category and paging params to the service")
        void forwardsFiltersAndPaging() throws Exception {
            // Mock the service to return an empty paged response
            when(productService.search(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageResponse<>(List.of(), 1, 5, 0));

            // Perform a GET request with all query parameters
            mockMvc.perform(get("/api/v1/products")
                            .param("name", "mouse")
                            .param("category", "Electronics")
                            .param("page", "1")
                            .param("size", "5")
                            .param("sort", "name")
                            .param("direction", "DESC"))
                    .andExpect(status().isOk());

            // Create an ArgumentCaptor to capture the Pageable object passed to the service
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            // Verify the service was called with the correct filter values;
            // capture the third argument (Pageable) for further assertion
            verify(productService).search(eq("mouse"), eq("Electronics"), captor.capture());
            // Assert that the captured Pageable matches what PageRequestFactory produces
            // with the same parameters: page=1, size=5, sort="name", direction="DESC"
            assertThat(captor.getValue())
                    .isEqualTo(PageRequestFactory.fromParams(1, 5, "name", "DESC"));
        }

        @Test
        @DisplayName("non-numeric page is rejected with 400, not 500")
        void invalidPageTypeIsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/products").param("page", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));

            verify(productService, never()).search(any(), any(), any());
        }

        @Test
        @DisplayName("unknown sort property → 400, not 500")
        void unknownSortPropertyIsBadRequest() throws Exception {
            when(productService.search(any(), any(), any(Pageable.class)))
                    .thenThrow(new PropertyReferenceException(
                            "notAColumn",
                            TypeInformation.of(Product.class),
                            List.of()));

            mockMvc.perform(get("/api/v1/products").param("sort", "notAColumn"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Unknown sort property 'notAColumn'"));
        }
    }

    // ---------------------------------------------------------------------
    // GET /api/v1/products/{id}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/products/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("returns 200 and the product")
        void returnsProduct() throws Exception {
            when(productService.getById(1L)).thenReturn(sampleProduct());

            mockMvc.perform(get("/api/v1/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sku").value("ELEC-001"))
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("unknown id → 404 with ProblemDetail body")
        void unknownIdReturns404() throws Exception {
            when(productService.getById(99L)).thenThrow(new NotFoundException("Product", 99L));

            mockMvc.perform(get("/api/v1/products/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.path").value("/api/v1/products/99"));
        }
    }

    // ---------------------------------------------------------------------
    // POST /api/v1/products
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/products")
    class CreateTests {

        @Test
        @DisplayName("valid body → 201 + Location header + created product")
        void createsProduct() throws Exception {
            when(productService.create(any(CreateProductRequest.class))).thenReturn(sampleProduct());

            String body = """
                    {
                      "sku": "ELEC-001",
                      "name": "Wireless Mouse",
                      "costPrice": 25.00,
                      "sellPrice": 45.00,
                      "lowStockThreshold": 5
                    }
                    """;

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "http://localhost/api/v1/products/1"))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.sku").value("ELEC-001"));
        }

        @Test
        @DisplayName("blank sku and negative sellPrice → 400 with per-field errors")
        void validationFailure() throws Exception {
            String body = """
                    {
                      "sku": " ",
                      "name": "Wireless Mouse",
                      "sellPrice": -5
                    }
                    """;

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.errors.sku").exists())
                    .andExpect(jsonPath("$.errors.sellPrice").exists());

            verify(productService, never()).create(any());
        }

        @Test
        @DisplayName("duplicate SKU → 409")
        void duplicateSkuReturns409() throws Exception {
            when(productService.create(any(CreateProductRequest.class)))
                    .thenThrow(new ConflictException("Product with SKU 'ELEC-001' already exists"));

            String body = """
                    {"sku": "ELEC-001", "name": "Mouse", "sellPrice": 45.00}
                    """;

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value("CONFLICT"));
        }

        @Test
        @DisplayName("malformed JSON → 400")
        void malformedJsonIsBadRequest() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sku\": "))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------------------------------------------------------------------
        // PATCH /api/v1/products/{id}
    // ---------------------------------------------------------------------

    @Nested
        @DisplayName("PATCH /api/v1/products/{id}")
    class UpdateTests {

        @Test
        @DisplayName("partial body → 200 and updated product")
        void updatesProduct() throws Exception {
            when(productService.update(eq(1L), any(UpdateProductRequest.class))).thenReturn(sampleProduct());

            mockMvc.perform(patch("/api/v1/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Wireless Mouse v2\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));

            ArgumentCaptor<UpdateProductRequest> captor = ArgumentCaptor.forClass(UpdateProductRequest.class);
            verify(productService).update(eq(1L), captor.capture());
            assertThat(captor.getValue().name()).isEqualTo("Wireless Mouse v2");
            assertThat(captor.getValue().sellPrice()).isNull();
        }

        @Test
        @DisplayName("unknown id → 404")
        void unknownIdReturns404() throws Exception {
            when(productService.update(eq(99L), any(UpdateProductRequest.class)))
                    .thenThrow(new NotFoundException("Product", 99L));

            mockMvc.perform(patch("/api/v1/products/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Whatever\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------------
    // DELETE /api/v1/products/{id}
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/products/{id}")
    class DeleteTests {

        @Test
        @DisplayName("soft delete → 204 with empty body")
        void deletesProduct() throws Exception {
            mockMvc.perform(delete("/api/v1/products/1"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().doesNotExist("Content-Type"));

            verify(productService).deactivate(1L);
        }

        @Test
        @DisplayName("unknown id → 404")
        void unknownIdReturns404() throws Exception {
            doThrow(new NotFoundException("Product", 99L)).when(productService).deactivate(99L);

            mockMvc.perform(delete("/api/v1/products/99"))
                    .andExpect(status().isNotFound());
        }
    }
}
