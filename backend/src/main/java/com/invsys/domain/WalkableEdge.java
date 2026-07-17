package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "walkable_edges")
public class WalkableEdge extends TenantScopedEntity {

    @Column(name = "node_a_id", nullable = false)
    private UUID nodeAId;

    @Column(name = "node_b_id", nullable = false)
    private UUID nodeBId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal distance;

    public UUID getNodeAId() {
        return nodeAId;
    }

    public void setNodeAId(UUID nodeAId) {
        this.nodeAId = nodeAId;
    }

    public UUID getNodeBId() {
        return nodeBId;
    }

    public void setNodeBId(UUID nodeBId) {
        this.nodeBId = nodeBId;
    }

    public BigDecimal getDistance() {
        return distance;
    }

    public void setDistance(BigDecimal distance) {
        this.distance = distance;
    }
}
