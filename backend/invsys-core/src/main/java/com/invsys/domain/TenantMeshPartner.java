package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "tenant_mesh_partners")
public class TenantMeshPartner extends TenantScopedEntity {

    @Column(name = "partner_tenant_id", nullable = false)
    private UUID partnerTenantId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "connection_status", nullable = false)
    private String connectionStatus = "PENDING";

    public UUID getPartnerTenantId() {
        return partnerTenantId;
    }

    public void setPartnerTenantId(UUID partnerTenantId) {
        this.partnerTenantId = partnerTenantId;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }
}
