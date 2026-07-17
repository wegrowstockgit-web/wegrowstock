package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.Lot;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.LotRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LotServiceTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID VARIANT = UUID.fromString("a0000000-0000-4000-8000-000000000801");

    @Mock LotRepository lotRepository;
    @Mock ProductVariantRepository productVariantRepository;

    private LotService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new LotService(lotRepository, productVariantRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void generateInternalLotNumberUsesIntPrefixTimestampAndRandom() {
        String lot = LotService.generateInternalLotNumber();
        assertThat(lot).matches("INT-[0-9A-Z]+-\\d{4}");
    }

    @Test
    void buildLotLabelZplContainsLotSkuAndBarcodeCommands() {
        String zpl = LotService.buildLotLabelZpl("INT-ABC-1234", "WIDGET-S");
        assertThat(zpl).startsWith("^XA");
        assertThat(zpl).contains("INT-ABC-1234");
        assertThat(zpl).contains("WIDGET-S");
        assertThat(zpl).contains("^BCN");
        assertThat(zpl).contains("^XZ");
    }

    @Test
    void mintInternalLotPersistsLotAndReturnsZpl() {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT);
        variant.setTenantId(TENANT);
        variant.setSku("WIDGET-S");
        variant.setLotTracked(true);

        when(productVariantRepository.findById(VARIANT)).thenReturn(Optional.of(variant));
        when(lotRepository.saveAndFlush(any(Lot.class))).thenAnswer(inv -> {
            Lot lot = inv.getArgument(0);
            lot.setId(UUID.randomUUID());
            return lot;
        });

        LotService.MintedLot minted = service.mintInternalLot(TENANT, VARIANT);

        assertThat(minted.lotNumber()).startsWith("INT-");
        assertThat(minted.sku()).isEqualTo("WIDGET-S");
        assertThat(minted.zpl()).contains(minted.lotNumber());
        assertThat(minted.zpl()).contains("^XA");

        ArgumentCaptor<Lot> captor = ArgumentCaptor.forClass(Lot.class);
        verify(lotRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getLotNumber()).isEqualTo(minted.lotNumber());
        assertThat(captor.getValue().getVariantId()).isEqualTo(VARIANT);
    }

    @Test
    void mintInternalLotRejectsNonLotTrackedVariant() {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT);
        variant.setTenantId(TENANT);
        variant.setSku("PLAIN");
        variant.setLotTracked(false);
        when(productVariantRepository.findById(VARIANT)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> service.mintInternalLot(TENANT, VARIANT))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("lot-tracked");
    }
}
