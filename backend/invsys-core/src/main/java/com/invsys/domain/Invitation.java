package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "invitations")
public class Invitation extends TenantScopedEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    /** Extra role codes beyond {@code role_id}, comma-separated (additive invite). */
    @Column(name = "additional_roles", length = 512)
    private String additionalRoles;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(UUID invitedBy) {
        this.invitedBy = invitedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
    }

    public String getAdditionalRoles() {
        return additionalRoles;
    }

    public void setAdditionalRoles(String additionalRoles) {
        this.additionalRoles = additionalRoles;
    }
}
