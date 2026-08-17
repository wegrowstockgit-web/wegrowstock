package com.invsys.pos;

import com.invsys.core.security.RequireModule;
import com.invsys.domain.subscription.AppModule;
import com.invsys.pos.dto.OfflineReceiptDto;
import com.invsys.pos.dto.PosCatalogItem;
import com.invsys.pos.dto.PosSyncResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/pos")
@RequireModule(AppModule.RETAIL_POS)
public class PosSyncController {

    private final PosReceiptProcessor processor;
    private final PosSessionService sessionService;

    public PosSyncController(PosReceiptProcessor processor, PosSessionService sessionService) {
        this.processor = processor;
        this.sessionService = sessionService;
    }

    @PostMapping("/sync-receipts")
    public PosSyncResponse syncReceipts(@Valid @RequestBody @NotEmpty List<@Valid OfflineReceiptDto> receipts) {
        return processor.processReceipts(receipts);
    }

    @GetMapping("/catalog-sync")
    public List<PosCatalogItem> catalogSync() {
        return sessionService.syncCatalog();
    }

    @GetMapping("/catalog/lookup")
    public PosCatalogItem lookupCatalog(@RequestParam("upc") String upc) {
        return sessionService.lookupByUpc(upc);
    }
}
