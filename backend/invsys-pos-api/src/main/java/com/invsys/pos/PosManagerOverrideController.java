package com.invsys.pos;

import com.invsys.core.security.RequireModule;
import com.invsys.domain.subscription.AppModule;
import com.invsys.pos.dto.PosManagerOverrideResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos")
@RequireModule(AppModule.RETAIL_POS)
public class PosManagerOverrideController {

    private final PosManagerOverrideService managerOverrideService;

    public PosManagerOverrideController(PosManagerOverrideService managerOverrideService) {
        this.managerOverrideService = managerOverrideService;
    }

    @GetMapping("/manager-overrides")
    public PosManagerOverrideResponse managerOverrides() {
        return managerOverrideService.currentManagers();
    }
}
