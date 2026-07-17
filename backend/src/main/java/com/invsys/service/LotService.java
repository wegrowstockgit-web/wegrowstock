package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Lot;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.LotRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
public class LotService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LotRepository lotRepository;
    private final ProductVariantRepository productVariantRepository;

    public LotService(LotRepository lotRepository, ProductVariantRepository productVariantRepository) {
        this.lotRepository = lotRepository;
        this.productVariantRepository = productVariantRepository;
    }

    /**
     * Escape hatch: mint a unique internal lot when the vendor barcode/lot is damaged or missing.
     */
    @Transactional
    public MintedLot mintInternalLot(UUID tenantId, UUID variantId) {
        UUID contextTenant = TenantContext.requireTenantId();
        if (!contextTenant.equals(tenantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_MISMATCH", "Tenant mismatch");
        }

        ProductVariant variant = productVariantRepository.findById(variantId)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Variant not found"));

        if (!variant.isLotTracked()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VARIANT_NOT_LOT_TRACKED",
                    "Internal lot minting requires a lot-tracked variant");
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            String lotNumber = generateInternalLotNumber();
            try {
                Lot lot = new Lot();
                lot.setTenantId(tenantId);
                lot.setVariantId(variantId);
                lot.setLotNumber(lotNumber);
                lot.setReceivedAt(Instant.now());
                Lot saved = lotRepository.saveAndFlush(lot);
                return new MintedLot(
                        saved.getId(),
                        saved.getLotNumber(),
                        variant.getId(),
                        variant.getSku(),
                        buildLotLabelZpl(saved.getLotNumber(), variant.getSku()));
            } catch (DataIntegrityViolationException ex) {
                // Unique (tenant, variant, lot_number) collision — retry with a new number.
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "LOT_MINT_COLLISION",
                "Could not mint a unique internal lot number");
    }

    /** {@code INT-} + epoch-ms Base36 + random int — readable and highly unique. */
    static String generateInternalLotNumber() {
        String ts = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        int suffix = RANDOM.nextInt(9000) + 1000;
        return "INT-" + ts + "-" + suffix;
    }

    static String buildLotLabelZpl(String lotNumber, String sku) {
        String safeLot = sanitizeZpl(lotNumber);
        String safeSku = sanitizeZpl(sku != null ? sku : "");
        return """
                ^XA
                ^FO40,40^A0N,36,36^FDINTERNAL LOT^FS
                ^FO40,90^A0N,48,48^FD%s^FS
                ^FO40,160^A0N,28,28^FDSKU %s^FS
                ^FO40,210^BCN,90,Y,N,N^FD%s^FS
                ^FO40,330^A0N,22,22^FDMinted for compliance tracking^FS
                ^XZ
                """.formatted(safeLot, safeSku, safeLot).replace("\r", "").strip() + "\n";
    }

    private static String sanitizeZpl(String value) {
        return value.replace("^", "").replace("~", "");
    }

    public record MintedLot(
            UUID id,
            String lotNumber,
            UUID variantId,
            String sku,
            String zpl
    ) {
    }
}
