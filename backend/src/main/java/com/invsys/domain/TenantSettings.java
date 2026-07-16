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
@Table(name = "tenant_settings")
public class TenantSettings extends TenantScopedEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> settings = new LinkedHashMap<>();

    @Column(name = "alert_email", length = 255)
    private String alertEmail;

    @Column(name = "slack_webhook_url", length = 1024)
    private String slackWebhookUrl;

    public Map<String, Object> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    public String getAlertEmail() {
        return alertEmail;
    }

    public void setAlertEmail(String alertEmail) {
        this.alertEmail = alertEmail;
    }

    public String getSlackWebhookUrl() {
        return slackWebhookUrl;
    }

    public void setSlackWebhookUrl(String slackWebhookUrl) {
        this.slackWebhookUrl = slackWebhookUrl;
    }

    public static TenantSettings withDefaults(UUID tenantId) {
        TenantSettings ts = new TenantSettings();
        ts.setTenantId(tenantId);
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("currency", "USD");
        defaults.put("timezone", "America/New_York");
        defaults.put("allow_negative_inventory", false);
        defaults.put("allow_blind_receiving", false);
        defaults.put("over_receipt_tolerance_percent", 0);
        defaults.put("barcode_prefix", "");
        defaults.put("barcode_suffix", "");
        defaults.put("default_reorder_point", 10);
        defaults.put("default_reorder_qty", 50);
        defaults.put("invoice_number_format", "INV-{YYYY}-{seq:5}");
        defaults.put("sku_template", "SKU-{PREFIX}-{ID:5}");
        defaults.put("barcode_template", "BC-{ID:8}");
        defaults.put("sku_prefix", "INV");
        defaults.put("costing_method", "MOVING_AVERAGE");
        defaults.put("platform_fee_percent", 0.4);
        defaults.put("payment_terms_days", 30);
        ts.setSettings(defaults);
        return ts;
    }
}
