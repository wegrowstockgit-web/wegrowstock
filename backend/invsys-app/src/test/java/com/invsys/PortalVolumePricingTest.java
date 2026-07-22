package com.invsys;

import com.invsys.api.dto.PortalCatalogItemResponse;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.domain.CustomerCatalogRestriction;
import com.invsys.domain.CustomerPriceTier;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.VolumePriceBreak;
import com.invsys.modules.sales.repository.CustomerCatalogRestrictionRepository;
import com.invsys.modules.sales.repository.CustomerPriceTierRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.VolumePriceBreakRepository;
import com.invsys.service.PortalService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PortalVolumePricingTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired PortalService portalService;
    @Autowired CustomerRepository customerRepository;
    @Autowired CustomerPriceTierRepository priceTierRepository;
    @Autowired CustomerCatalogRestrictionRepository catalogRestrictionRepository;
    @Autowired VolumePriceBreakRepository volumePriceBreakRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void catalogRestrictionsAndVolumeBreaksReplaceTier() {
        UUID tenantId = testDataHelper.createTenant("Portal Vol", "pvol-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        CustomerPriceTier tier = new CustomerPriceTier();
        tier.setTenantId(tenantId);
        tier.setName("Gold");
        tier.setDiscountPercent(new BigDecimal("5"));
        tier = priceTierRepository.save(tier);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Portal Customer");
        customer.setPriceTierId(tier.getId());
        customer = customerRepository.save(customer);

        Product allowed = new Product();
        allowed.setTenantId(tenantId);
        allowed.setSkuRoot("ALLOW");
        allowed.setName("Allowed Product");
        allowed = productRepository.save(allowed);

        Product blocked = new Product();
        blocked.setTenantId(tenantId);
        blocked.setSkuRoot("BLOCK");
        blocked.setName("Blocked Product");
        blocked = productRepository.save(blocked);

        ProductVariant allowedVariant = new ProductVariant();
        allowedVariant.setTenantId(tenantId);
        allowedVariant.setProductId(allowed.getId());
        allowedVariant.setSku("ALLOW-1");
        allowedVariant.setPrice(new BigDecimal("100.0000"));
        allowedVariant = variantRepository.save(allowedVariant);

        ProductVariant blockedVariant = new ProductVariant();
        blockedVariant.setTenantId(tenantId);
        blockedVariant.setProductId(blocked.getId());
        blockedVariant.setSku("BLOCK-1");
        blockedVariant.setPrice(new BigDecimal("50.0000"));
        blockedVariant = variantRepository.save(blockedVariant);

        CustomerCatalogRestriction restriction = new CustomerCatalogRestriction();
        restriction.setTenantId(tenantId);
        restriction.setCustomerId(customer.getId());
        restriction.setTargetType("PRODUCT");
        restriction.setTargetId(allowed.getId());
        catalogRestrictionRepository.save(restriction);

        VolumePriceBreak breakAt1 = new VolumePriceBreak();
        breakAt1.setTenantId(tenantId);
        breakAt1.setVariantId(allowedVariant.getId());
        breakAt1.setMinQuantity(BigDecimal.ONE);
        breakAt1.setDiscountPercent(new BigDecimal("10"));
        volumePriceBreakRepository.save(breakAt1);

        VolumePriceBreak breakAt10 = new VolumePriceBreak();
        breakAt10.setTenantId(tenantId);
        breakAt10.setVariantId(allowedVariant.getId());
        breakAt10.setMinQuantity(new BigDecimal("10"));
        breakAt10.setDiscountPercent(new BigDecimal("20"));
        volumePriceBreakRepository.save(breakAt10);

        TenantContext.setCustomerId(customer.getId());

        List<PortalCatalogItemResponse> catalog = portalService.catalog();
        assertThat(catalog).hasSize(1);
        assertThat(catalog.getFirst().variantId()).isEqualTo(allowedVariant.getId());
        assertThat(catalog.getFirst().unitPrice()).isEqualByComparingTo("90.0000");

        var order = portalService.createOrder(List.of(
                new PortalService.PortalOrderLineInput(allowedVariant.getId(), new BigDecimal("10"))));
        assertThat(order.total()).isEqualByComparingTo("800.0000");
    }
}
