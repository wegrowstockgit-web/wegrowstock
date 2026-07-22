package com.invsys.api;

import com.invsys.api.dto.SerialScanResponse;
import com.invsys.core.common.ApiException;
import com.invsys.service.SerialScanQueryService;
import io.micrometer.core.annotation.Timed;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.invsys.api.dto.ScanLookupResponse;
import com.invsys.service.ScanService;

@RestController
@RequestMapping("/api/v1/scan")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER')")
public class ScanController {

    private final com.invsys.service.ScanService scanService;
    private final SerialScanQueryService serialScanQueryService;

    public ScanController(com.invsys.service.ScanService scanService,
                          SerialScanQueryService serialScanQueryService) {
        this.scanService = scanService;
        this.serialScanQueryService = serialScanQueryService;
    }

    @GetMapping("/{barcode}")
    @Timed(value = "invsys.scan.lookup", description = "Barcode lookup hot path")
    public com.invsys.api.dto.ScanLookupResponse lookup(@PathVariable String barcode) {
        return scanService.lookup(barcode);
    }

    @GetMapping("/serial/{serialNumber}")
    @Timed(value = "invsys.scan.serial", description = "Serial lookup hot path")
    public SerialScanResponse lookupSerial(@PathVariable String serialNumber) {
        SerialScanResponse result = serialScanQueryService.lookup(serialNumber);
        if (result == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Serial number not found");
        }
        return result;
    }
}
