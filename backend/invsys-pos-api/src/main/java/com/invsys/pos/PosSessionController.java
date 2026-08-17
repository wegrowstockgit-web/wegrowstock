package com.invsys.pos;

import com.invsys.core.security.RequireModule;
import com.invsys.domain.subscription.AppModule;
import com.invsys.pos.dto.PosManagerOverrideResponse;
import com.invsys.pos.dto.PosSessionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos")
public class PosSessionController {

    private final PosSessionService sessionService;
    private final PosManagerOverrideService managerOverrideService;

    public PosSessionController(
            PosSessionService sessionService,
            PosManagerOverrideService managerOverrideService) {
        this.sessionService = sessionService;
        this.managerOverrideService = managerOverrideService;
    }

    /**
     * Cashier bootstrap. Not module-gated so a locked tenant receives
     * {@code posEnabled=false} instead of HTTP 402.
     */
    @GetMapping("/session")
    public PosSessionResponse session(
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            @RequestParam(value = "timezone", required = false) String timezone,
            @RequestParam(value = "placeLanguage", required = false) String placeLanguage,
            @RequestParam(value = "placeCurrency", required = false) String placeCurrency) {
        return sessionService.currentSession(acceptLanguage, timezone, placeLanguage, placeCurrency);
    }

    /**
     * Morning sync of {@code pos.supervise} PIN hashes for offline manager overrides.
     */
    @GetMapping("/managers/sync-pins")
    @RequireModule(AppModule.RETAIL_POS)
    public PosManagerOverrideResponse syncManagerPins() {
        return managerOverrideService.currentManagers();
    }
}
