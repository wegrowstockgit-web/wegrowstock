package com.invsys.modules.inventory.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class InventoryLevelDeltaFlushRepositoryImpl implements InventoryLevelDeltaFlushRepository {

    private final JdbcTemplate bootstrapJdbc;
    private final JdbcTemplate tenantJdbc;
    private final TransactionTemplate bootstrapTx;

    public InventoryLevelDeltaFlushRepositoryImpl(
            @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
            DataSource dataSource) {
        this.bootstrapJdbc = new JdbcTemplate(bootstrapDataSource);
        this.tenantJdbc = new JdbcTemplate(dataSource);
        this.bootstrapTx = new TransactionTemplate(new DataSourceTransactionManager(bootstrapDataSource));
    }

    @Override
    public int flushBatch(int limit) {
        Integer applied = bootstrapTx.execute(status -> {
            List<ClaimedDelta> claimed = claimPending(limit);
            if (claimed.isEmpty()) {
                return 0;
            }
            applyBatch(claimed);
            return claimed.size();
        });
        return applied != null ? applied : 0;
    }

    private List<ClaimedDelta> claimPending(int limit) {
        return bootstrapJdbc.query(
                """
                SELECT id, tenant_id, variant_id, location_id, lot_id, lpn_id,
                       on_hand_delta, owner_customer_id
                FROM inventory_level_deltas
                WHERE applied_at IS NULL
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """,
                (rs, rowNum) -> new ClaimedDelta(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("tenant_id")),
                        UUID.fromString(rs.getString("variant_id")),
                        UUID.fromString(rs.getString("location_id")),
                        (UUID) rs.getObject("lot_id"),
                        (UUID) rs.getObject("lpn_id"),
                        rs.getBigDecimal("on_hand_delta"),
                        (UUID) rs.getObject("owner_customer_id")),
                limit);
    }

    private void applyBatch(List<ClaimedDelta> claimed) {
        record AggKey(UUID tenantId, UUID variantId, UUID locationId, UUID lotId, UUID lpnId) {
        }
        record Agg(BigDecimal onHand, UUID ownerCustomerId, List<UUID> ids) {
        }

        Map<AggKey, Agg> aggregates = new HashMap<>();
        for (ClaimedDelta d : claimed) {
            AggKey key = new AggKey(d.tenantId(), d.variantId(), d.locationId(), d.lotId(), d.lpnId());
            Agg existing = aggregates.get(key);
            if (existing == null) {
                List<UUID> ids = new ArrayList<>();
                ids.add(d.id());
                aggregates.put(key, new Agg(d.onHandDelta(), d.ownerCustomerId(), ids));
            } else {
                existing.ids().add(d.id());
                aggregates.put(key, new Agg(
                        existing.onHand().add(d.onHandDelta()),
                        d.ownerCustomerId() != null ? d.ownerCustomerId() : existing.ownerCustomerId(),
                        existing.ids()));
            }
        }

        for (Map.Entry<AggKey, Agg> entry : aggregates.entrySet()) {
            AggKey key = entry.getKey();
            Agg agg = entry.getValue();
            bootstrapJdbc.update(
                    """
                    INSERT INTO inventory_levels (
                        tenant_id, variant_id, location_id, lot_id, lpn_id, on_hand, allocated, owner_customer_id
                    )
                    VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                    ON CONFLICT (tenant_id, variant_id, location_id, lot_id, lpn_id)
                    DO UPDATE SET
                        on_hand = inventory_levels.on_hand + EXCLUDED.on_hand,
                        owner_customer_id = COALESCE(EXCLUDED.owner_customer_id, inventory_levels.owner_customer_id),
                        updated_at = NOW()
                    """,
                    key.tenantId(),
                    key.variantId(),
                    key.locationId(),
                    key.lotId(),
                    key.lpnId(),
                    agg.onHand(),
                    agg.ownerCustomerId());
        }

        bootstrapJdbc.batchUpdate(
                "UPDATE inventory_level_deltas SET applied_at = NOW() WHERE id = ?",
                claimed,
                claimed.size(),
                (ps, d) -> ps.setObject(1, d.id()));
    }

    @Override
    public BigDecimal sumPendingOnHand(UUID tenantId, UUID variantId, UUID locationId, UUID lotId) {
        BigDecimal sum = tenantJdbc.queryForObject(
                """
                SELECT COALESCE(SUM(on_hand_delta), 0)
                FROM inventory_level_deltas
                WHERE tenant_id = ?
                  AND variant_id = ?
                  AND location_id = ?
                  AND lot_id IS NOT DISTINCT FROM ?
                  AND applied_at IS NULL
                """,
                BigDecimal.class,
                tenantId,
                variantId,
                locationId,
                lotId);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal sumPendingOnHandForLpn(UUID tenantId, UUID lpnId) {
        BigDecimal sum = tenantJdbc.queryForObject(
                """
                SELECT COALESCE(SUM(on_hand_delta), 0)
                FROM inventory_level_deltas
                WHERE tenant_id = ?
                  AND lpn_id = ?
                  AND applied_at IS NULL
                """,
                BigDecimal.class,
                tenantId,
                lpnId);
        return sum != null ? sum : BigDecimal.ZERO;
    }
}
