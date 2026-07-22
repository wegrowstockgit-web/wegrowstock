package com.invsys;

import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.service.SerialNumberService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.invsys.core.common.ApiException;

@SpringBootTest
@ActiveProfiles("test")
class SerialNumberServiceTest extends AbstractIntegrationTest {

    @Autowired SerialNumberService serialNumberService;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired ProductRepository productRepository;
    @Autowired TestDataHelper testDataHelper;

    private UUID tenantId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        tenantId = testDataHelper.createTenant("Serial Test Co", "serial-test-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("SER");
        product.setName("Serialized Gadget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("SER-001");
        variant.setPrice(BigDecimal.TEN);
        variant.setTrackSerials(true);
        variant = variantRepository.save(variant);
        variantId = variant.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void serializedVariantRejectsQuantityOtherThanOne() {
        ProductVariant variant = variantRepository.findById(variantId).orElseThrow();
        assertThatThrownBy(() -> serialNumberService.validateSerializedQuantity(variant, BigDecimal.valueOf(5)))
                .hasMessageContaining("exactly 1 or -1")
                .satisfies(ex -> {
                    com.invsys.core.common.ApiException api = (com.invsys.core.common.ApiException) ex;
                    org.assertj.core.api.Assertions.assertThat(api.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                });
    }
}
