package com.invsys.service;

import com.invsys.domain.UserSavedView;
import com.invsys.repository.UserSavedViewRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSavedViewServiceTest {

    @Mock UserSavedViewRepository repository;
    UserSavedViewService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UserSavedViewService(repository, new ObjectMapper());
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void saveAcceptsRawJsonStringAndUpsertsByName() {
        when(repository.findByTenantIdAndUserIdAndGridIdentifierAndName(
                tenantId, userId, "products", "Purchasing Layout"))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSavedView.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSavedView saved = service.saveForCurrentUser(
                "Purchasing Layout",
                "products",
                "{\"columnVisibility\":{\"barcode\":false},\"pinnedColumns\":[\"reorder\"],\"columnOrder\":[\"sku\"]}");

        ArgumentCaptor<UserSavedView> captor = ArgumentCaptor.forClass(UserSavedView.class);
        verify(repository).save(captor.capture());
        UserSavedView entity = captor.getValue();
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getGridIdentifier()).isEqualTo("products");
        assertThat(entity.getName()).isEqualTo("Purchasing Layout");
        assertThat(entity.getStateJson()).containsKey("columnVisibility");
        assertThat(saved.getStateJson().get("pinnedColumns")).isInstanceOf(java.util.List.class);
    }

    @Test
    void saveAcceptsMapState() {
        when(repository.findByTenantIdAndUserIdAndGridIdentifierAndName(
                tenantId, userId, "products", "Ops"))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserSavedView.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveForCurrentUser("Ops", "products", Map.of(
                "columnVisibility", Map.of("barcode", false),
                "pinnedColumns", java.util.List.of("sku", "name"),
                "columnOrder", java.util.List.of("sku", "name", "barcode")));

        verify(repository).save(any(UserSavedView.class));
    }
}
