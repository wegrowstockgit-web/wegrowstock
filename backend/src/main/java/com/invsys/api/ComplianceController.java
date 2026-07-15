package com.invsys.api;

import com.invsys.api.dto.ComplianceLotTraceResponse;
import com.invsys.common.ApiException;
import com.invsys.service.InventoryGenealogyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Compliance / recall surface — multi-directional lot genealogy.
 */
@RestController
@RequestMapping("/api/v1/compliance")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
public class ComplianceController {

    private final InventoryGenealogyService genealogyService;

    public ComplianceController(InventoryGenealogyService genealogyService) {
        this.genealogyService = genealogyService;
    }

    @GetMapping("/lot-trace")
    public ComplianceLotTraceResponse lotTrace(
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) String lotNumber) {
        if (lotId == null && (lotNumber == null || lotNumber.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "lotId or lotNumber is required");
        }
        return genealogyService.complianceTrace(lotId, lotNumber);
    }
}
