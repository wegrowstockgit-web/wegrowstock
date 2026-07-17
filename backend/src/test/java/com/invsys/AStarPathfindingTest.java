package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Location;
import com.invsys.repository.LocationRepository;
import com.invsys.service.PickingService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AStarPathfindingTest extends AbstractIntegrationTest {

    @Autowired AuthService authService;
    @Autowired LocationRepository locationRepository;
    @Autowired PickingService pickingService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void wayfindingReturnsPolylineBetweenBins() {
        String slug = "ast-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "AStar Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Location wh = loc(tenantId, null, "WAREHOUSE", "WH", "/WH", 0, null, null);
        Location zone = loc(tenantId, wh.getId(), "ZONE", "Z1", "/WH/Z1", 0, null, null);
        Location aisle = loc(tenantId, zone.getId(), "AISLE", "A1", "/WH/Z1/A1", 0, null, null);
        Location from = loc(tenantId, aisle.getId(), "BIN", "B1", "/WH/Z1/A1/B1", 1,
                new BigDecimal("0"), new BigDecimal("0"));
        Location mid = loc(tenantId, aisle.getId(), "BIN", "B2", "/WH/Z1/A1/B2", 2,
                new BigDecimal("10"), new BigDecimal("0"));
        Location to = loc(tenantId, aisle.getId(), "BIN", "B3", "/WH/Z1/A1/B3", 3,
                new BigDecimal("20"), new BigDecimal("0"));

        PickingService.WayfindingPath path = pickingService.wayfinding(from.getId(), to.getId());
        assertThat(path.points()).isNotEmpty();
        assertThat(path.travelCost()).isGreaterThan(0);
        assertThat(path.points().getFirst().locationId()).isEqualTo(from.getId());
        assertThat(path.points().getLast().locationId()).isIn(to.getId(), mid.getId(), from.getId());
        assertThat(path.points().size()).isGreaterThanOrEqualTo(2);
    }

    private Location loc(UUID tenantId, UUID parentId, String type, String code, String path,
                         int seq, BigDecimal x, BigDecimal y) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setParentLocationId(parentId);
        location.setType(type);
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        location.setSequenceIndex(seq);
        location.setCoordX(x);
        location.setCoordY(y);
        return locationRepository.save(location);
    }
}
