package com.invsys.api;

import com.invsys.api.dto.LotTraceResponse;
import com.invsys.service.InventoryGenealogyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compliance/lots")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
public class ComplianceLotController {

    private final InventoryGenealogyService genealogyService;

    public ComplianceLotController(InventoryGenealogyService genealogyService) {
        this.genealogyService = genealogyService;
    }

    @GetMapping("/{lotId}/trace")
    public LotTraceResponse traceById(@PathVariable UUID lotId) {
        return genealogyService.traceByLotId(lotId);
    }

    @GetMapping("/by-number")
    public LotTraceResponse traceByNumber(@RequestParam String lotNumber) {
        return genealogyService.traceByLotNumber(lotNumber);
    }
}
