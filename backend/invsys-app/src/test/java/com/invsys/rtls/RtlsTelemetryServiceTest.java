package com.invsys.rtls;

import com.invsys.core.common.ApiException;
import com.invsys.domain.RtlsPositionEvent;
import com.invsys.domain.RtlsTag;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.RtlsPositionEventRepository;
import com.invsys.repository.RtlsTagRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RtlsTelemetryServiceTest {

    @Mock RtlsTagRepository tagRepository;
    @Mock RtlsPositionEventRepository positionRepository;
    @Mock LocationRepository locationRepository;
    @Mock RtlsSseHub sseHub;

    RtlsTelemetryService service;
    UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new RtlsTelemetryService(tagRepository, positionRepository, locationRepository, sseHub);
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void bleAoaInterpolatesCartesianAndPublishes() {
        when(tagRepository.findByTenantIdAndTagId(tenantId, "T1")).thenReturn(Optional.empty());
        when(tagRepository.save(any(RtlsTag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(positionRepository.save(any(RtlsPositionEvent.class))).thenAnswer(inv -> {
            RtlsPositionEvent e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        var frames = service.ingest(List.of(new RtlsTelemetryService.TelemetryPacket(
                "T1", "BLE_AOA", null, null, null,
                BigDecimal.valueOf(90), BigDecimal.TEN, null, null, null, null, null)));

        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().x().doubleValue()).isCloseTo(0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(frames.getFirst().y().doubleValue()).isCloseTo(10, org.assertj.core.data.Offset.offset(0.01));
        assertThat(frames.getFirst().technology()).isEqualTo("BLE_AOA");
        verify(sseHub).publish(any(), any());
    }

    @Test
    void uwbUsesExplicitCoordinates() {
        RtlsTag existing = new RtlsTag();
        existing.setTenantId(tenantId);
        existing.setTagId("U1");
        existing.setTechnology("UWB");
        existing.setAssetType("VEHICLE");
        existing.setAssetRef(UUID.randomUUID());
        when(tagRepository.findByTenantIdAndTagId(tenantId, "U1")).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(RtlsPositionEvent.class))).thenAnswer(inv -> {
            RtlsPositionEvent e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        var frames = service.ingest(List.of(new RtlsTelemetryService.TelemetryPacket(
                "U1", "UWB", BigDecimal.valueOf(12.5), BigDecimal.valueOf(3.25), BigDecimal.ONE,
                null, null, null, null, null, null, null)));

        assertThat(frames.getFirst().x()).isEqualByComparingTo("12.5");
        assertThat(frames.getFirst().y()).isEqualByComparingTo("3.25");
        assertThat(frames.getFirst().assetType()).isEqualTo("VEHICLE");
        assertThat(frames.getFirst().assetRef()).isEqualTo(existing.getAssetRef());
    }

    @Test
    void rejectsPacketWithoutCoordinatesOrAzimuth() {
        assertThatThrownBy(() -> service.ingest(List.of(new RtlsTelemetryService.TelemetryPacket(
                "X", "UWB", null, null, null, null, null, null, null, null, null, null))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("x/y");
    }

    @Test
    void upsertTagNormalizesTechnologyAndAssetType() {
        when(tagRepository.findByTenantIdAndTagId(tenantId, "TAG-9")).thenReturn(Optional.empty());
        when(tagRepository.save(any(RtlsTag.class))).thenAnswer(inv -> inv.getArgument(0));

        RtlsTag saved = service.upsertTag(new RtlsTelemetryService.UpsertTagRequest(
                "TAG-9", "ultra-wideband", "pallet", null, "Pallet", true));

        ArgumentCaptor<RtlsTag> captor = ArgumentCaptor.forClass(RtlsTag.class);
        verify(tagRepository).save(captor.capture());
        assertThat(captor.getValue().getTechnology()).isEqualTo("UWB");
        assertThat(captor.getValue().getAssetType()).isEqualTo("PALLET");
        assertThat(saved.getLabel()).isEqualTo("Pallet");
    }
}
