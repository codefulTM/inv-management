package com.codefultm.inventory_management.product;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.codefultm.inventory_management.common.dto.ProductDtos.CreateProductRequest;
import com.codefultm.inventory_management.common.dto.ProductDtos.ProductResponse;
import com.codefultm.inventory_management.common.dto.ProductDtos.UpdateProductRequest;
import com.codefultm.inventory_management.common.paging.PageRequestFactory;
import com.codefultm.inventory_management.common.paging.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * HTTP layer for the {@code product} slice.
 *
 * <p>Thin on purpose: no business rule lives here. The controller only
 * <ul>
 *   <li>maps HTTP to service calls (query params → paging, body → DTO),</li>
 *   <li>declares the API contract ({@link Valid}, status codes, {@code Location}),</li>
 *   <li>lets {@code GlobalExceptionHandler} translate exceptions into ProblemDetail JSON.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalog: search, create, update, soft delete")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Lists active products with optional name / category filters.
     *
     * @param name      case-insensitive fragment of the product name
     * @param category  exact category name
     * @param page      0-based page index (default 0)
     * @param size      page size, capped at 100 (default 20)
     * @param sort      entity property to sort by (default {@code createdAt})
     * @param direction {@code ASC} or {@code DESC} (default {@code ASC})
     */
    @Operation(summary = "List products", description = "Active products only; filters are cumulative.")
    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        Pageable pageable = PageRequestFactory.fromParams(page, size, sort, direction);
        return productService.search(name, category, pageable);
    }

    /** Returns one active product, or 404 when it does not exist / was soft-deleted. */
    @Operation(summary = "Get a product by id")
    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    /**
     * Creates a product.
     *
     * <p>Answers {@code 201 Created} with a {@code Location} header pointing at the
     * new resource, so the client does not have to guess the id.
     */
    @Operation(summary = "Create a product")
    @ApiResponse(responseCode = "201", description = "Product created",
            headers = @Header(name = "Location", description = "URL of the new product"))
    @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    @ApiResponse(responseCode = "409", description = "SKU already taken", content = @Content)
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    /** Partial update: {@code null} fields in the body are left unchanged. */
    @Operation(summary = "Update a product", description = "Partial update — null fields keep their current value.")
    @PatchMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    /**
     * Soft delete: the row stays in the database, the product simply stops being served.
     * Returns {@code 204 No Content}.
     */
    @Operation(summary = "Soft-delete a product")
    @ApiResponse(responseCode = "204", description = "Product deactivated")
    @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.deactivate(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
