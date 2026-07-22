package com.invsys.api;

import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.service.LotService;
import com.invsys.modules.inventory.service.LpnService;
import com.invsys.core.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryLevelRepository levelRepository;
    private final LotService lotService;
    private final LpnService lpnService;

    public InventoryController(InventoryService inventoryService,
                               InventoryLevelRepository levelRepository,
                               LotService lotService,
                               LpnService lpnService) {
        this.inventoryService = inventoryService;
        this.levelRepository = levelRepository;
        this.lotService = lotService;
        this.lpnService = lpnService;
    }

    @GetMapping("/levels")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<InventoryLevel> levels(@RequestParam(required = false) UUID variantId) {
        if (variantId != null) {
            return levelRepository.findByTenantIdAndVariantId(TenantContext.requireTenantId(), variantId);
        }
        return levelRepository.findAll();
    }

    @PostMapping("/lots/mint")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public LotService.MintedLot mintInternalLot(@Valid @RequestBody MintLotRequest request) {
        return lotService.mintInternalLot(TenantContext.requireTenantId(), request.variantId());
    }

    @PostMapping("/lpns")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public LicensePlateResponse createLpn(@Valid @RequestBody CreateLpnRequest request) {
        var lpn = inventoryService.createLicensePlate(request.lpnBarcode(), request.locationId());
        return new LicensePlateResponse(lpn.getId(), lpn.getLpnBarcode(), lpn.getLocationId(), lpn.getStatus());
    }

    @PostMapping("/lpns/mint")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public LpnService.MintedLpn mintLpn(@RequestBody(required = false) MintLpnRequest request) {
        return lpnService.mint(request != null ? request.locationId() : null);
    }

    @PostMapping("/lpns/{lpnBarcode}/pack")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public LpnService.PackResult packLpn(@PathVariable String lpnBarcode,
                                         @RequestBody(required = false) PackLpnRequest request) {
        PackLpnRequest body = request != null ? request : new PackLpnRequest(List.of(), List.of(), List.of());
        return lpnService.pack(lpnBarcode, body.inventoryLevelIds(), body.allocationIds(), body.barcodes());
    }

    @GetMapping("/lpns/{lpnBarcode}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public LpnService.LpnContents lpnContents(@PathVariable String lpnBarcode) {
        return lpnService.contents(lpnBarcode);
    }

    @PostMapping("/lpns/move")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public InventoryService.MoveLpnResult moveLpn(@Valid @RequestBody MoveLpnRequest request) {
        UUID destinationId = request.destinationLocationId();
        if (destinationId == null && request.destinationBarcode() != null
                && !request.destinationBarcode().isBlank()) {
            destinationId = inventoryService.resolveLocationId(request.destinationBarcode());
        }
        if (destinationId == null) {
            throw new com.invsys.core.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "DESTINATION_REQUIRED",
                    "destinationLocationId or destinationBarcode is required");
        }
        return inventoryService.moveLpn(
                TenantContext.requireTenantId(),
                request.lpnBarcode(),
                destinationId);
    }

    @PostMapping("/lpns/{lpnId}/receive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public InventoryLedger receiveOntoLpn(@PathVariable UUID lpnId, @Valid @RequestBody ReceiveOntoLpnRequest request) {
        return inventoryService.receiveOntoLpn(request.variantId(), lpnId, request.quantity());
    }

    @PostMapping("/receive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public InventoryLedger receive(@Valid @RequestBody ReceiveRequest request) {
        Map<String, Object> metadata = null;
        if (request.managerOverridePin() != null && !request.managerOverridePin().isBlank()) {
            metadata = Map.of("managerOverridePin", request.managerOverridePin().trim());
        }
        return inventoryService.receive(request.variantId(), request.locationId(), request.lotId(), null,
                request.quantity(), null, request.referenceType(), request.referenceId(),
                null, null, metadata);
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public InventoryLedger adjust(@Valid @RequestBody AdjustRequest request) {
        return inventoryService.adjust(request.variantId(), request.locationId(), request.lotId(),
                request.delta(), request.reasonCode());
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
    public UUID transfer(@Valid @RequestBody TransferRequest request) {
        return inventoryService.transfer(request.variantId(), request.fromLocationId(), request.toLocationId(),
                request.lotId(), request.quantity(), request.managerOverridePin());
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<InventoryLedger> ledger(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) UUID variantId) {
        return inventoryService.listRecentLedger(limit, variantId);
    }

    @PostMapping("/ledger/{ledgerId}/reverse")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public InventoryLedger reverse(@PathVariable UUID ledgerId) {
        return inventoryService.reverseLedgerEntry(ledgerId);
    }

    public record MintLotRequest(@NotNull UUID variantId) {
    }

    public record CreateLpnRequest(String lpnBarcode, @NotNull UUID locationId) {
    }

    public record MintLpnRequest(UUID locationId) {
    }

    public record PackLpnRequest(
            List<UUID> inventoryLevelIds,
            List<UUID> allocationIds,
            List<String> barcodes
    ) {
    }

    public record MoveLpnRequest(
            @NotNull String lpnBarcode,
            UUID destinationLocationId,
            String destinationBarcode
    ) {
    }

    public record ReceiveOntoLpnRequest(@NotNull UUID variantId, @NotNull BigDecimal quantity) {
    }

    public record LicensePlateResponse(UUID id, String lpnBarcode, UUID locationId, String status) {
    }

    public record ReceiveRequest(
            @NotNull UUID variantId,
            @NotNull UUID locationId,
            UUID lotId,
            @NotNull BigDecimal quantity,
            String referenceType,
            UUID referenceId,
            String managerOverridePin
    ) {
    }

    public record AdjustRequest(
            @NotNull UUID variantId,
            @NotNull UUID locationId,
            UUID lotId,
            @NotNull BigDecimal delta,
            String reasonCode
    ) {
    }

    public record TransferRequest(
            @NotNull UUID variantId,
            @NotNull UUID fromLocationId,
            @NotNull UUID toLocationId,
            UUID lotId,
            @NotNull BigDecimal quantity,
            String managerOverridePin
    ) {
    }
}
