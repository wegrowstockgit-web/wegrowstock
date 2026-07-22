package com.invsys.modules.purchasing.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "suppliers")
public class Supplier extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> contact = new LinkedHashMap<>();

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "tax_id")
    private String taxId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency", length = 3)
    private String defaultCurrency;

    @Column(name = "business_registration", length = 128)
    private String businessRegistration;

    /** Stored masked / last-4 friendly; never log in plaintext audit trails. */
    @Column(name = "bank_account_iban", length = 64)
    private String bankAccountIban;

    @Column(name = "bank_routing_number", length = 64)
    private String bankRoutingNumber;

    @Column(name = "default_lead_time_days")
    private Integer defaultLeadTimeDays;

    @Column(name = "minimum_order_quantity_value", precision = 19, scale = 4)
    private BigDecimal minimumOrderQuantityValue;

    @Column(name = "supplier_rating", precision = 5, scale = 2)
    private BigDecimal supplierRating;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getContact() {
        return contact;
    }

    public void setContact(Map<String, Object> contact) {
        this.contact = contact;
    }

    @JsonProperty("contactEmail")
    public String getContactEmail() {
        Object email = contact != null ? contact.get("email") : null;
        return email != null ? email.toString() : null;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
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

    public String getBusinessRegistration() {
        return businessRegistration;
    }

    public void setBusinessRegistration(String businessRegistration) {
        this.businessRegistration = businessRegistration;
    }

    public String getBankAccountIban() {
        return bankAccountIban;
    }

    public void setBankAccountIban(String bankAccountIban) {
        this.bankAccountIban = bankAccountIban;
    }

    public String getBankRoutingNumber() {
        return bankRoutingNumber;
    }

    public void setBankRoutingNumber(String bankRoutingNumber) {
        this.bankRoutingNumber = bankRoutingNumber;
    }

    public Integer getDefaultLeadTimeDays() {
        return defaultLeadTimeDays;
    }

    public void setDefaultLeadTimeDays(Integer defaultLeadTimeDays) {
        this.defaultLeadTimeDays = defaultLeadTimeDays;
    }

    public BigDecimal getMinimumOrderQuantityValue() {
        return minimumOrderQuantityValue;
    }

    public void setMinimumOrderQuantityValue(BigDecimal minimumOrderQuantityValue) {
        this.minimumOrderQuantityValue = minimumOrderQuantityValue;
    }

    public BigDecimal getSupplierRating() {
        return supplierRating;
    }

    public void setSupplierRating(BigDecimal supplierRating) {
        this.supplierRating = supplierRating;
    }
}
