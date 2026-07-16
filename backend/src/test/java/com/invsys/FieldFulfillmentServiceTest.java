package com.invsys;

import com.invsys.api.dto.VehicleAssignmentResponse;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.User;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.service.FieldFulfillmentService;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FieldFulfillmentServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired FieldFulfillmentService fieldService;
    @Autowired InventoryService inventoryService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLevelRepository levelRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void assignReplenishAndConsumeFromVan() {
        UUID tenantId = testDataHelper.createTenant("Field", "field-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        User tech = new User();
        tech.setTenantId(tenantId);
        tech.setEmail("tech@" + UUID.randomUUID() + ".test");
        tech.setDisplayName("Tech");
        tech.setPasswordHash(passwordEncoder.encode("password123"));
        tech.setStatus("ACTIVE");
        tech = userRepository.save(tech);
        TenantContext.setUserId(tech.getId());

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("VAN");
        product.setName("Van Part");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("VAN-1");
        ProductVariant savedVariant = variantRepository.save(variant);

        Location warehouse = new Location();
        warehouse.setTenantId(tenantId);
        warehouse.setType("WAREHOUSE");
        warehouse.setCode("WH-VAN");
        warehouse.setName("Depot");
        warehouse.setPath("/WH-VAN");
        Location savedWarehouse = locationRepository.save(warehouse);

        Location vehicle = new Location();
        vehicle.setTenantId(tenantId);
        vehicle.setType("VEHICLE");
        vehicle.setCode("VAN-01");
        vehicle.setName("Truck 1");
        vehicle.setPath("/VAN-01");
        Location savedVehicle = locationRepository.save(vehicle);

        inventoryService.receive(savedVariant.getId(), savedWarehouse.getId(), null, new BigDecimal("10"), null, null);

        VehicleAssignmentResponse assignment = fieldService.assignVehicle(savedVehicle.getId(), tech.getId());
        assertThat(assignment.returnedAt()).isNull();
        assertThat(fieldService.activeAssignmentForUser(tech.getId()))
                .isPresent()
                .get()
                .extracting(VehicleAssignmentResponse::locationId)
                .isEqualTo(savedVehicle.getId());

        fieldService.replenishVan(savedWarehouse.getId(), savedVehicle.getId(), Map.of(savedVariant.getId(), new BigDecimal("4")));
        entityManager.clear();
        BigDecimal vanOnHand = levelRepository.findByTenantIdAndLocationId(tenantId, savedVehicle.getId()).stream()
                .filter(l -> l.getVariantId().equals(savedVariant.getId()))
                .map(com.invsys.domain.InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(vanOnHand).isEqualByComparingTo("4");

        fieldService.consumeFromVan(savedVariant.getId(), new BigDecimal("1"), "SERVICE_CONSUMPTION");
        entityManager.clear();
        BigDecimal vanAfter = levelRepository.findByTenantIdAndLocationId(tenantId, savedVehicle.getId()).stream()
                .filter(l -> l.getVariantId().equals(savedVariant.getId()))
                .map(com.invsys.domain.InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(vanAfter).isEqualByComparingTo("3");

        fieldService.returnVehicle(assignment.id());
        assertThat(fieldService.findActiveAssignment(tenantId, tech.getId())).isEmpty();
    }
}
