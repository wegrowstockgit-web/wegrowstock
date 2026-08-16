package com.invsys.pos;

import com.invsys.core.security.RequireModule;
import com.invsys.domain.subscription.AppModule;
import com.invsys.pos.dto.OfflineReceiptDto;
import com.invsys.pos.dto.PosSyncResponse;
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
public class PosSyncController {

    private final PosReceiptProcessor processor;

    public PosSyncController(PosReceiptProcessor processor) {
        this.processor = processor;
    }

    @PostMapping("/sync-receipts")
    public PosSyncResponse syncReceipts(@Valid @RequestBody @NotEmpty List<@Valid OfflineReceiptDto> receipts) {
        return processor.sync(receipts);
    }
}
