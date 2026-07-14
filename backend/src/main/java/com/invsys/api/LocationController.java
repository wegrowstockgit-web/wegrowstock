package com.invsys.api;

import com.invsys.domain.Location;
import com.invsys.repository.LocationRepository;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationRepository locationRepository;

    public LocationController(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER','PICKER','VIEWER')")
    public List<Location> list(@RequestParam(required = false) String type) {
        UUID tenantId = TenantContext.requireTenantId();
        if (type != null && !type.isBlank()) {
            return locationRepository.findByTenantIdAndType(tenantId, type);
        }
        return locationRepository.findByTenantIdOrderByPathAsc(tenantId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
    public Location create(@Valid @RequestBody CreateLocationRequest request) {
        Location location = new Location();
        location.setTenantId(TenantContext.requireTenantId());
        location.setParentLocationId(request.parentLocationId());
        location.setType(request.type());
        location.setCode(request.code());
        location.setName(request.name());
        location.setPath(request.path());
        return locationRepository.save(location);
    }

    public record CreateLocationRequest(
            UUID parentLocationId,
            @NotBlank String type,
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String path
    ) {
    }
}
