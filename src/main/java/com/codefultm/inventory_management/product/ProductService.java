package com.codefultm.inventory_management.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codefultm.inventory_management.common.dto.ProductDtos.CreateProductRequest;
import com.codefultm.inventory_management.common.dto.ProductDtos.ProductResponse;
import com.codefultm.inventory_management.common.dto.ProductDtos.UpdateProductRequest;
import com.codefultm.inventory_management.common.error.ConflictException;
import com.codefultm.inventory_management.common.error.NotFoundException;
import com.codefultm.inventory_management.common.paging.PageResponse;
import com.codefultm.inventory_management.product.persistence.Product;
import com.codefultm.inventory_management.product.persistence.ProductRepository;
import com.codefultm.inventory_management.product.persistence.ProductSpecifications;

/**
 * Business logic for the {@code product} slice.
 *
 * <p>Rules enforced here (not in the controller, not in the repository):
 * <ul>
 *   <li>a SKU must be unique among all products — duplicate → {@link ConflictException} (409);</li>
 *   <li>only active products are visible; deleting is a soft delete ({@code active = false});</li>
 *   <li>a missing or already-deactivated product → {@link NotFoundException} (404).</li>
 * </ul>
 *
 * <p>Read-only queries run in {@code readOnly = true} transactions so Hibernate
 * skips dirty checking and the connection can be routed to a read replica later.
 */
@Service
public class ProductService {

    private static final String ENTITY = "Product";

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    /**
     * Lists active products, filtered by any combination of name fragment and category name.
     *
     * <p>Both filters are optional and <b>cumulative</b>: passing both narrows the result
     * to products that match the name <em>and</em> belong to that category. The predicates
     * are composed with {@link Specification}, so the query stays a single SQL statement
     * instead of one derived method per filter combination.
     *
     * @param name         case-insensitive fragment of the product name, may be {@code null} or blank
     * @param categoryName exact category name, may be {@code null} or blank
     * @param pageable     paging + sorting instructions
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String name, String categoryName, Pageable pageable) {
        Specification<Product> specification = ProductSpecifications.active();

        if (name != null && !name.isBlank()) {
            specification = specification.and(ProductSpecifications.nameContains(name.strip()));
        }
        if (categoryName != null && !categoryName.isBlank()) {
            specification = specification.and(ProductSpecifications.inCategoryNamed(categoryName.strip()));
        }

        return toPageResponse(productRepository.findAll(specification, pageable));
    }

    /** Returns a single active product or throws 404. */
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return productMapper.toResponse(requireActiveProduct(id));
    }

    /** Returns a single active product by SKU or throws 404. */
    @Transactional(readOnly = true)
    public ProductResponse getBySku(String sku) {
        Product product = productRepository.findBySkuAndActiveTrue(sku)
                .orElseThrow(() -> new NotFoundException("Product not found with SKU '" + sku + "'"));
        return productMapper.toResponse(product);
    }

    /**
     * Creates a product.
     *
     * @throws ConflictException when the SKU is already taken (including by a
     *                           soft-deleted product — SKUs stay reserved)
     */
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("Product with SKU '" + request.sku() + "' already exists");
        }
        Product saved = productRepository.save(productMapper.toEntity(request));
        return productMapper.toResponse(saved);
    }

    /** Applies a partial update to an existing active product. */
    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = requireActiveProduct(id);
        productMapper.updateEntity(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    /** Soft delete: the row stays in the DB but is hidden from every query. */
    @Transactional
    public void deactivate(Long id) {
        Product product = requireActiveProduct(id);
        product.setActive(false);
        productRepository.save(product);
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    private Product requireActiveProduct(Long id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException(ENTITY, id));
    }

    private PageResponse<ProductResponse> toPageResponse(Page<Product> page) {
        return new PageResponse<>(
                page.getContent().stream().map(productMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }
}
