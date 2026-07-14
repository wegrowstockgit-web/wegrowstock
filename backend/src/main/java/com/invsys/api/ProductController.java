package com.invsys.api;

import com.invsys.domain.Product;
import com.invsys.repository.ProductRepository;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<Product> list() {
        return productRepository.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(TenantContext.requireTenantId());
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
