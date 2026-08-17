package com.invsys.pos;

import com.invsys.core.security.RequireModule;
import com.invsys.domain.subscription.AppModule;
import com.invsys.pos.dto.PosAuditEventDto;
import com.invsys.pos.dto.PosAuditSyncResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/pos")
@RequireModule(AppModule.RETAIL_POS)
public class PosAuditSyncController {

    private final PosAuditSyncService auditSyncService;

    public PosAuditSyncController(PosAuditSyncService auditSyncService) {
        this.auditSyncService = auditSyncService;
    }

    @PostMapping("/audit-sync")
    public PosAuditSyncResponse syncAuditEvents(
            @Valid @RequestBody @NotEmpty List<@Valid PosAuditEventDto> events) {
        return auditSyncService.sync(events);
    }
}
