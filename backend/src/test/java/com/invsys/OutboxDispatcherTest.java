package com.invsys;

import com.invsys.domain.OutboxEvent;
import com.invsys.integration.OutboxDispatcher;
import com.invsys.integration.OutboxService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "invsys.integration.outbox.dispatcher-enabled=true",
        "spring.task.scheduling.enabled=false"
})
class OutboxDispatcherTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired OutboxService outboxService;
    @Autowired OutboxDispatcher outboxDispatcher;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void dispatcherPublishesPendingEventsPerTenant() {
        UUID tenantId = testDataHelper.createTenant("Outbox Tenant", "outbox-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = outboxService.append(
                "PRODUCT_VARIANT", aggregateId, "UNKNOWN_TEST_EVENT", Map.of("variantId", aggregateId.toString()));

        assertThat(event.getPublishedAt()).isNull();
        UUID eventId = event.getId();
        TenantContext.clear();

        JdbcTemplate jdbc = new JdbcTemplate(bootstrapDataSource);
        for (int attempt = 0; attempt < 50; attempt++) {
            transactionTemplate.executeWithoutResult(status -> outboxDispatcher.dispatchNext());
            Integer publishedCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM outbox_events WHERE id = ? AND published_at IS NOT NULL AND status = 'PUBLISHED'",
                    Integer.class,
                    eventId);
            if (publishedCount != null && publishedCount == 1) {
                return;
            }
        }

        Integer publishedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE id = ? AND published_at IS NOT NULL AND status = 'PUBLISHED'",
                Integer.class,
                eventId);
        assertThat(publishedCount).isEqualTo(1);
    }
}
