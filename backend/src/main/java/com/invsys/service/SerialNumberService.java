package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SerialNumber;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SerialNumberRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class SerialNumberService {

    private final SerialNumberRepository serialNumberRepository;
    private final ProductVariantRepository variantRepository;

    public SerialNumberService(SerialNumberRepository serialNumberRepository,
                               ProductVariantRepository variantRepository) {
        this.serialNumberRepository = serialNumberRepository;
        this.variantRepository = variantRepository;
    }

    public void validateSerializedQuantity(ProductVariant variant, BigDecimal quantity) {
        if (variant.isTrackSerials()) {
            BigDecimal abs = quantity.abs();
            if (abs.compareTo(BigDecimal.ONE) != 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SERIAL_QTY_INVALID",
                        "Serialized items require quantity of exactly 1 or -1 per ledger row");
            }
        }
    }

    public ProductVariant requireVariant(UUID variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
    }

    @Transactional
    public SerialNumber receiveSerial(UUID variantId, String serialCode) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductVariant variant = requireVariant(variantId);
        if (!variant.isTrackSerials()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_SERIALIZED",
                    "Variant does not track serial numbers");
        }
        serialNumberRepository.findByTenantIdAndVariantIdAndSerialNumber(tenantId, variantId, serialCode)
                .ifPresent(existing -> {
                    if (!"SCRAPPED".equals(existing.getStatus())) {
                        throw new ApiException(HttpStatus.CONFLICT, "SERIAL_EXISTS",
                                "Serial number already exists: " + serialCode);
                    }
                });

        SerialNumber serial = serialNumberRepository
                .findByTenantIdAndVariantIdAndSerialNumber(tenantId, variantId, serialCode)
                .orElseGet(() -> {
                    SerialNumber created = new SerialNumber();
                    created.setTenantId(tenantId);
                    created.setVariantId(variantId);
                    created.setSerialNumber(serialCode);
                    created.setStatus("IN_STOCK");
                    return created;
                });
        serial.setStatus("IN_STOCK");
        return serialNumberRepository.save(serial);
    }

    @Transactional
    public SerialNumber consumeSerial(UUID variantId, String serialCode) {
        UUID tenantId = TenantContext.requireTenantId();
        SerialNumber serial = serialNumberRepository
                .findByTenantIdAndVariantIdAndSerialNumber(tenantId, variantId, serialCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERIAL_NOT_FOUND",
                        "Serial number not found: " + serialCode));
        assertAssignable(serial);
        serial.setStatus("SHIPPED");
        return serialNumberRepository.save(serial);
    }

    public void assertAssignable(SerialNumber serial) {
        if ("SHIPPED".equals(serial.getStatus()) || "SCRAPPED".equals(serial.getStatus())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SERIAL_UNAVAILABLE",
                    "Serial number is not available for outbound movement");
        }
    }

    public void assertAssignable(UUID serialNumberId) {
        serialNumberRepository.findById(serialNumberId).ifPresent(this::assertAssignable);
    }
}
