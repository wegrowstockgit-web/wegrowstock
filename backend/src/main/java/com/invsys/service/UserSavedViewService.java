package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.UserSavedView;
import com.invsys.repository.UserSavedViewRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserSavedViewService {

    private final UserSavedViewRepository repository;
    private final ObjectMapper objectMapper;

    public UserSavedViewService(UserSavedViewRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<UserSavedView> listForCurrentUser(String gridIdentifier) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        return repository.findByTenantIdAndUserIdAndGridIdentifierOrderByCreatedAtAsc(
                tenantId, userId, gridIdentifier.trim());
    }

    @Transactional
    public UserSavedView saveForCurrentUser(String name, String gridIdentifier, Object state) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        String trimmedName = name == null ? "" : name.trim();
        String grid = gridIdentifier == null ? "" : gridIdentifier.trim();
        if (trimmedName.isBlank() || grid.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "name and gridIdentifier are required");
        }
        Map<String, Object> stateMap = toStateMap(state);

        UserSavedView view = repository
                .findByTenantIdAndUserIdAndGridIdentifierAndName(tenantId, userId, grid, trimmedName)
                .orElseGet(UserSavedView::new);
        view.setTenantId(tenantId);
        view.setUserId(userId);
        view.setGridIdentifier(grid);
        view.setName(trimmedName);
        view.setStateJson(stateMap);
        return repository.save(view);
    }

    @Transactional(readOnly = true)
    public UserSavedView getOwned(UUID viewId) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = TenantContext.requireUserId();
        return repository.findByIdAndTenantIdAndUserId(viewId, tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Saved view not found"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStateMap(Object state) {
        if (state == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "state is required");
        }
        if (state instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        if (state instanceof String raw) {
            String trimmed = raw.trim();
            if (trimmed.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "state is required");
            }
            try {
                return objectMapper.readValue(trimmed, Map.class);
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "state must be valid JSON");
            }
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "state must be a JSON object or string");
    }
}
