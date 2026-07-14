package com.invsys.api;

import com.invsys.domain.Location;
import com.invsys.repository.LocationRepository;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        List<Location> locations;
        if (type != null && !type.isBlank()) {
            locations = locationRepository.findByTenantIdAndType(tenantId, type);
        } else {
            locations = locationRepository.findByTenantIdOrderByPathAsc(tenantId);
        }

        if ("WAREHOUSE".equalsIgnoreCase(type) && !isElevated()) {
            Set<UUID> allowed = TenantContext.getAuthorizedWarehouseIds().stream().collect(Collectors.toSet());
            return locations.stream().filter(loc -> allowed.contains(loc.getId())).toList();
        }
        return locations;
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

    private static boolean isElevated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_OWNER".equals(a) || "ROLE_ADMIN".equals(a));
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
