package com.invsys.api;

import com.invsys.domain.PalletManifest;
import com.invsys.domain.PalletManifestItem;
import com.invsys.modules.inventory.domain.LicensePlate;
import com.invsys.modules.inventory.repository.LicensePlateRepository;
import com.invsys.service.PalletManifestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pallet-manifests")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class PalletManifestController {

    private final PalletManifestService palletManifestService;
    private final LicensePlateRepository licensePlateRepository;

    public PalletManifestController(PalletManifestService palletManifestService,
                                    LicensePlateRepository licensePlateRepository) {
        this.palletManifestService = palletManifestService;
        this.licensePlateRepository = licensePlateRepository;
    }

    @GetMapping
    public List<PalletManifestView> listManifests() {
        return palletManifestService.listManifests().stream().map(this::toView).toList();
    }

    @GetMapping("/active")
    public PalletManifestView activeManifest() {
        return palletManifestService.findActiveBuilding()
                .map(this::toView)
                .orElse(null);
    }

    @GetMapping("/{id}")
    public PalletManifestView getManifest(@PathVariable UUID id) {
        return toView(palletManifestService.getManifest(id));
    }

    @PostMapping
    public PalletManifestView createManifest(@RequestBody(required = false) CreatePalletManifestRequest request) {
        UUID warehouseId = request != null ? request.warehouseId() : null;
        String carrier = request != null
                ? (request.carrierName() != null ? request.carrierName() : request.carrier())
                : null;
        return toView(palletManifestService.createManifest(warehouseId, carrier));
    }

    @PostMapping("/{id}/items")
    public PalletManifestItemResponse addItem(@PathVariable UUID id,
                                              @Valid @RequestBody AddPalletItemRequest request) {
        return toItemResponse(palletManifestService.addItem(id, request.lpnId(), request.shipmentId()));
    }

    @PostMapping("/{id}/lpns")
    public PalletManifestView addLpnByBarcode(@PathVariable UUID id,
                                              @Valid @RequestBody AddLpnBarcodeRequest request) {
        palletManifestService.addItemByLpnBarcode(id, request.lpnBarcode());
        return toView(palletManifestService.getManifest(id));
    }

    @PostMapping("/{id}/seal")
    public PalletManifestView seal(@PathVariable UUID id,
                                   @RequestBody(required = false) SealPalletRequest request) {
        String bol = request != null ? request.bolNumber() : null;
        return toView(palletManifestService.seal(id, bol));
    }

    @PostMapping("/{id}/dispatch")
    public PalletManifestView dispatch(@PathVariable UUID id) {
        return toView(palletManifestService.dispatch(id));
    }

    private PalletManifestView toView(PalletManifest manifest) {
        List<PalletManifestItemView> items = palletManifestService.listItems(manifest.getId()).stream()
                .map(item -> {
                    String barcode = item.getLpnId() == null ? null
                            : licensePlateRepository.findById(item.getLpnId())
                            .map(LicensePlate::getLpnBarcode)
                            .orElse(null);
                    return new PalletManifestItemView(item.getId(), item.getLpnId(), barcode, item.getShipmentId());
                })
                .toList();
        return new PalletManifestView(
                manifest.getId(),
                manifest.getSscc18(),
                manifest.getWarehouseId(),
                manifest.getCarrierName(),
                manifest.getStatus(),
                manifest.getBolNumber(),
                manifest.getCreatedAt(),
                items);
    }

    private PalletManifestItemResponse toItemResponse(PalletManifestItem item) {
        return new PalletManifestItemResponse(
                item.getId(),
                item.getPalletId(),
                item.getLpnId(),
                item.getShipmentId(),
                item.getCreatedAt());
    }

    public record CreatePalletManifestRequest(UUID warehouseId, String carrier, String carrierName) {
    }

    public record AddPalletItemRequest(UUID lpnId, UUID shipmentId) {
    }

    public record AddLpnBarcodeRequest(@NotBlank String lpnBarcode) {
    }

    public record SealPalletRequest(String bolNumber) {
    }

    public record PalletManifestView(
            UUID id,
            String sscc18,
            UUID warehouseId,
            String carrierName,
            String status,
            String bolNumber,
            Instant createdAt,
            List<PalletManifestItemView> items
    ) {
    }

    public record PalletManifestItemView(
            UUID id,
            UUID lpnId,
            String lpnBarcode,
            UUID shipmentId
    ) {
    }

    public record PalletManifestItemResponse(
            UUID id,
            UUID palletId,
            UUID lpnId,
            UUID shipmentId,
            Instant createdAt
    ) {
    }
}
