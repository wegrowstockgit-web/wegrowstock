package com.invsys.mesh;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeshCatalogTranslationServiceTest {

    @Test
    void encodeAndParsePartnerScopedExternalId() {
        UUID partner = UUID.randomUUID();
        UUID sellerVariant = UUID.randomUUID();
        String encoded = MeshCatalogTranslationService.encodePartnerVariant(partner, sellerVariant);
        assertThat(MeshCatalogTranslationService.parseSellerVariantId(encoded, partner))
                .contains(sellerVariant);
    }

    @Test
    void parsePlainUuidExternalId() {
        UUID partner = UUID.randomUUID();
        UUID sellerVariant = UUID.randomUUID();
        assertThat(MeshCatalogTranslationService.parseSellerVariantId(sellerVariant.toString(), partner))
                .contains(sellerVariant);
    }
}
