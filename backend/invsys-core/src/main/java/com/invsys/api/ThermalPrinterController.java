package com.invsys.api;

import com.invsys.domain.ThermalPrinter;
import com.invsys.service.ThermalPrintingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/thermal-printers")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class ThermalPrinterController {

    private final ThermalPrintingService thermalPrintingService;

    public ThermalPrinterController(ThermalPrintingService thermalPrintingService) {
        this.thermalPrintingService = thermalPrintingService;
    }

    @GetMapping
    public List<ThermalPrinterResponse> listPrinters() {
        return thermalPrintingService.listPrinters().stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ThermalPrinterResponse createPrinter(@Valid @RequestBody CreateThermalPrinterRequest request) {
        ThermalPrinter printer = thermalPrintingService.createPrinter(
                request.name(),
                request.printerType(),
                request.printnodePrinterId(),
                request.ipAddress(),
                request.port(),
                request.locationId(),
                request.isDefault());
        return toResponse(printer);
    }

    @PostMapping("/print-default")
    public PrintResponse printDefault(@Valid @RequestBody PrintZplRequest request) {
        ThermalPrinter printer = thermalPrintingService.resolveDefaultPrinter()
                .orElseThrow(() -> new com.invsys.core.common.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "PRINTER_NOT_FOUND",
                        "No default thermal printer configured"));
        thermalPrintingService.printZpl(printer.getId(), request.zpl());
        return new PrintResponse(printer.getId(), "SENT");
    }

    @PostMapping("/{id}/print")
    public PrintResponse print(@PathVariable UUID id, @Valid @RequestBody PrintZplRequest request) {
        thermalPrintingService.printZpl(id, request.zpl());
        return new PrintResponse(id, "SENT");
    }

    private ThermalPrinterResponse toResponse(ThermalPrinter printer) {
        return new ThermalPrinterResponse(
                printer.getId(),
                printer.getName(),
                printer.getPrinterType(),
                printer.getPrintnodePrinterId(),
                printer.getIpAddress(),
                printer.getPort(),
                printer.isDefault(),
                printer.getLocationId());
    }

    public record CreateThermalPrinterRequest(
            @NotBlank String name,
            @NotBlank String printerType,
            String printnodePrinterId,
            String ipAddress,
            Integer port,
            UUID locationId,
            boolean isDefault
    ) {
    }

    public record PrintZplRequest(@NotBlank String zpl) {
    }

    public record ThermalPrinterResponse(
            UUID id,
            String name,
            String printerType,
            String printnodePrinterId,
            String ipAddress,
            Integer port,
            boolean isDefault,
            UUID locationId
    ) {
    }

    public record PrintResponse(UUID printerId, String status) {
    }
}
