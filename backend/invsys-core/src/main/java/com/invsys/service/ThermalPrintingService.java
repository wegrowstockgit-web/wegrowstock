package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.ThermalPrinter;
import com.invsys.repository.ThermalPrinterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ThermalPrintingService {

    private static final String PRINTNODE_URL = "https://api.printnode.com";

    private final ThermalPrinterRepository printerRepository;
    private final RestClient printNodeClient;
    private final String printNodeApiKey;

    public ThermalPrintingService(
            ThermalPrinterRepository printerRepository,
            @Value("${invsys.printnode.api-key:${PRINTNODE_API_KEY:}}") String printNodeApiKey) {
        this.printerRepository = printerRepository;
        this.printNodeApiKey = printNodeApiKey == null ? "" : printNodeApiKey.trim();
        this.printNodeClient = RestClient.builder()
                .baseUrl(PRINTNODE_URL)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ThermalPrinter> listPrinters() {
        return printerRepository.findByTenantIdOrderByNameAsc(TenantContext.requireTenantId());
    }

    @Transactional
    public ThermalPrinter createPrinter(String name, String printerType, String printnodePrinterId,
                                          String ipAddress, Integer port, UUID locationId, boolean isDefault) {
        UUID tenantId = TenantContext.requireTenantId();
        validatePrinterConfig(printerType, printnodePrinterId, ipAddress, port);

        if (isDefault) {
            clearDefaultFlag(tenantId);
        }

        ThermalPrinter printer = new ThermalPrinter();
        printer.setTenantId(tenantId);
        printer.setName(name);
        printer.setPrinterType(printerType);
        printer.setPrintnodePrinterId(printnodePrinterId);
        printer.setIpAddress(ipAddress);
        printer.setPort(port);
        printer.setLocationId(locationId);
        printer.setDefault(isDefault);
        return printerRepository.save(printer);
    }

    @Transactional(readOnly = true)
    public Optional<ThermalPrinter> resolveDefaultPrinter() {
        UUID tenantId = TenantContext.requireTenantId();
        Optional<ThermalPrinter> defaultPrinter = printerRepository.findByTenantIdAndIsDefaultTrue(tenantId);
        if (defaultPrinter.isPresent()) {
            return defaultPrinter;
        }
        return printerRepository.findByTenantIdOrderByNameAsc(tenantId).stream().findFirst();
    }

    public void printZpl(UUID printerId, String zpl) {
        UUID tenantId = TenantContext.requireTenantId();
        ThermalPrinter printer = printerRepository.findByTenantIdAndId(tenantId, printerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRINTER_NOT_FOUND", "Thermal printer not found"));
        if (zpl == null || zpl.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ZPL_REQUIRED", "ZPL content is required");
        }
        switch (printer.getPrinterType()) {
            case "PRINTNODE" -> printViaPrintNode(printer, zpl);
            case "DIRECT_SOCKET" -> printViaSocket(printer, zpl);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PRINTER_TYPE",
                    "Unsupported printer type: " + printer.getPrinterType());
        }
    }

    public void printZplToDefault(String zpl) {
        resolveDefaultPrinter().ifPresentOrElse(
                printer -> printZpl(printer.getId(), zpl),
                () -> {
                    throw new ApiException(HttpStatus.NOT_FOUND, "NO_DEFAULT_PRINTER", "No thermal printer configured");
                });
    }

    public String buildSimpleLabelZpl(String title, String barcode, String subtitle) {
        String safeTitle = sanitizeZpl(title != null ? title : "LABEL");
        String safeBarcode = sanitizeZpl(barcode != null ? barcode : "");
        String safeSubtitle = sanitizeZpl(subtitle != null ? subtitle : "");
        return """
                ^XA
                ^FO40,40^A0N,36,36^FD%s^FS
                ^FO40,90^A0N,48,48^FD%s^FS
                ^FO40,160^BCN,100,Y,N,N^FD%s^FS
                ^FO40,300^A0N,22,22^FD%s^FS
                ^XZ
                """.formatted(safeTitle, safeBarcode, safeBarcode, safeSubtitle).replace("\r", "").strip() + "\n";
    }

    private void printViaPrintNode(ThermalPrinter printer, String zpl) {
        if (printNodeApiKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRINTNODE_NOT_CONFIGURED",
                    "PrintNode API key is not configured");
        }
        if (printer.getPrintnodePrinterId() == null || printer.getPrintnodePrinterId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRINTNODE_ID_REQUIRED",
                    "PrintNode printer id is required");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("printerId", Long.parseLong(printer.getPrintnodePrinterId().trim()));
        body.put("title", "InvSys Label");
        body.put("contentType", "raw_base64");
        body.put("content", Base64.getEncoder().encodeToString(zpl.getBytes(StandardCharsets.UTF_8)));
        body.put("source", "InvSys");

        printNodeClient.post()
                .uri("/printjobs")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(printNodeApiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void printViaSocket(ThermalPrinter printer, String zpl) {
        if (printer.getIpAddress() == null || printer.getIpAddress().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRINTER_IP_REQUIRED", "Printer IP address is required");
        }
        PrinterAddressValidator.assertSafePrinterTarget(printer.getIpAddress());
        int port = printer.getPort() != null ? printer.getPort() : 9100;
        byte[] payload = zpl.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printer.getIpAddress().trim(), port), 10_000);
            socket.setSoTimeout(10_000);
            try (OutputStream out = socket.getOutputStream()) {
                out.write(payload);
                out.flush();
            }
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SOCKET_PRINT_FAILED",
                    "Failed to send ZPL to printer: " + ex.getMessage());
        }
    }

    private void clearDefaultFlag(UUID tenantId) {
        printerRepository.findByTenantIdAndIsDefaultTrue(tenantId).ifPresent(existing -> {
            existing.setDefault(false);
            printerRepository.save(existing);
        });
    }

    private static void validatePrinterConfig(String printerType, String printnodePrinterId,
                                              String ipAddress, Integer port) {
        if (printerType == null || printerType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRINTER_TYPE_REQUIRED", "Printer type is required");
        }
        switch (printerType) {
            case "PRINTNODE" -> {
                if (printnodePrinterId == null || printnodePrinterId.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "PRINTNODE_ID_REQUIRED",
                            "PrintNode printer id is required");
                }
            }
            case "DIRECT_SOCKET" -> {
                if (ipAddress == null || ipAddress.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "PRINTER_IP_REQUIRED",
                            "Printer IP address is required");
                }
                PrinterAddressValidator.assertSafePrinterTarget(ipAddress);
                if (port != null && (port < 1 || port > 65535)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PORT", "Port must be between 1 and 65535");
                }
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PRINTER_TYPE",
                    "Unsupported printer type: " + printerType);
        }
    }

    private static String basicAuthHeader(String apiKey) {
        String token = Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private static String sanitizeZpl(String value) {
        return value.replace("^", "").replace("~", "");
    }
}
