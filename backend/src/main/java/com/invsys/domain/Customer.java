package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_address", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> billingAddress = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> shippingAddress = new LinkedHashMap<>();

    @Column(name = "stripe_customer_ref")
    private String stripeCustomerRef;

    @Column(name = "price_tier_id")
    private UUID priceTierId;

    @Column(name = "tax_id")
    private String taxId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency", length = 3)
    private String defaultCurrency;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Map<String, Object> getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(Map<String, Object> billingAddress) {
        this.billingAddress = billingAddress;
    }

    public Map<String, Object> getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(Map<String, Object> shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getStripeCustomerRef() {
        return stripeCustomerRef;
    }

    public void setStripeCustomerRef(String stripeCustomerRef) {
        this.stripeCustomerRef = stripeCustomerRef;
    }

    public UUID getPriceTierId() {
        return priceTierId;
    }

    public void setPriceTierId(UUID priceTierId) {
        this.priceTierId = priceTierId;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }
}
