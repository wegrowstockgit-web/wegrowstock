package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.modules.inventory.domain.CycleCount;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.inventory.repository.CycleCountRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TaskInterleavingHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;
    @Autowired CycleCountRepository cycleCountRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void nextBestActionReturnsClosestOpenCycleCount() throws Exception {
        String slug = "ti-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Interleave Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH-TI", "/WH-TI", 0);
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH-TI/Z1", 0);
        Location near = loc(tenantId, zone.getId(), "BIN", "NEAR", "/WH-TI/Z1/NEAR", 10);
        Location far = loc(tenantId, zone.getId(), "BIN", "FAR", "/WH-TI/Z1/FAR", 900);
        Location current = loc(tenantId, zone.getId(), "BIN", "HERE", "/WH-TI/Z1/HERE", 12);

        CycleCount farCount = new CycleCount();
        farCount.setTenantId(tenantId);
        farCount.setLocationId(far.getId());
        farCount.setStatus("IN_PROGRESS");
        farCount.setNotes("Far count");
        cycleCountRepository.save(farCount);

        CycleCount nearCount = new CycleCount();
        nearCount.setTenantId(tenantId);
        nearCount.setLocationId(near.getId());
        nearCount.setStatus("IN_PROGRESS");
        nearCount.setNotes("Near count");
        cycleCountRepository.save(nearCount);
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/tasks/next-best-action")
                        .param("currentLocationId", current.getId().toString())
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Warehouse-Id", wh.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskType").value("COUNT"))
                .andExpect(jsonPath("$.locationId").value(near.getId().toString()))
                .andExpect(jsonPath("$.instruction").value("Near count"));
    }

    private Location loc(UUID tenantId, UUID parentId, String type, String code, String path, int seq) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setParentLocationId(parentId);
        location.setType(type);
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        location.setSequenceIndex(seq);
        return locationRepository.save(location);
    }
}
