package com.invsys.api;

import com.invsys.domain.UserSavedView;
import com.invsys.service.UserSavedViewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/views")
@PreAuthorize("isAuthenticated()")
public class UserSavedViewController {

    private final UserSavedViewService service;

    public UserSavedViewController(UserSavedViewService service) {
        this.service = service;
    }

    @GetMapping
    public List<SavedViewResponse> list(@RequestParam("grid") String gridIdentifier) {
        return service.listForCurrentUser(gridIdentifier).stream()
                .map(SavedViewResponse::from)
                .toList();
    }

    @PostMapping
    public SavedViewResponse save(@Valid @RequestBody SaveViewRequest request) {
        Object state = request.state() != null ? request.state() : request.stateJson();
        UserSavedView saved = service.saveForCurrentUser(
                request.name(), request.gridIdentifier(), state);
        return SavedViewResponse.from(saved);
    }

    public record SaveViewRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 50) String gridIdentifier,
            /** Preferred: parsed JSON object of column layout. */
            Object state,
            /** Alternate: raw JSON string of the layout (prompt contract). */
            String stateJson
    ) {
    }

    public record SavedViewResponse(
            UUID id,
            String gridIdentifier,
            String name,
            Map<String, Object> state,
            Instant createdAt
    ) {
        static SavedViewResponse from(UserSavedView view) {
            return new SavedViewResponse(
                    view.getId(),
                    view.getGridIdentifier(),
                    view.getName(),
                    view.getStateJson(),
                    view.getCreatedAt());
        }
    }
}
