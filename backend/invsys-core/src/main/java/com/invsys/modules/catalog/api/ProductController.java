package com.invsys.modules.catalog.api;

import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.core.common.OffsetPaging;
import com.invsys.core.common.PageResponse;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    private static final Set<String> PRODUCT_SORT = Set.of("name", "skuRoot", "createdAt");

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public PageResponse<Product> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name,asc") String sort) {
        Page<Product> result = productRepository.search(
                TenantContext.requireTenantId(),
                OffsetPaging.keyword(search),
                OffsetPaging.of(page, size, sort, "name", Sort.Direction.ASC, PRODUCT_SORT));
        return PageResponse.of(result);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public Product create(@Valid @RequestBody CreateProductRequest request) {
        Product product = new Product();
        product.setTenantId(TenantContext.requireTenantId());
        product.setSkuRoot(request.skuRoot());
        product.setName(request.name());
        product.setDescription(request.description());
        return productRepository.save(product);
    }

    public record CreateProductRequest(@NotBlank String skuRoot, @NotBlank String name, String description) {
    }
}
