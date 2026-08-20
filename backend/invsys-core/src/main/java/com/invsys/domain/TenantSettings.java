package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "tenant_settings")
@EntityListeners(TenantSettingsCacheEvictListener.class)
public class TenantSettings extends TenantScopedEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> settings = new LinkedHashMap<>();

    @Column(name = "alert_email", length = 255)
    private String alertEmail;

    @Column(name = "slack_webhook_url", length = 1024)
    private String slackWebhookUrl;

    @Column(name = "rma_auto_approve_max_value", nullable = false)
    private BigDecimal rmaAutoApproveMaxValue = new BigDecimal("100.00");

    @Column(name = "blind_cycle_counts", nullable = false)
    private boolean blindCycleCounts = true;

    @Column(name = "max_auto_adjust_value", nullable = false)
    private BigDecimal maxAutoAdjustValue = new BigDecimal("100.00");

    @Column(name = "predictive_replenishment_enabled", nullable = false)
    private boolean predictiveReplenishmentEnabled = true;

    @Column(name = "desktop_idle_timeout_minutes", nullable = false)
    private int desktopIdleTimeoutMinutes = 30;

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

    public BigDecimal getRmaAutoApproveMaxValue() {
        return rmaAutoApproveMaxValue;
    }

    public void setRmaAutoApproveMaxValue(BigDecimal rmaAutoApproveMaxValue) {
        this.rmaAutoApproveMaxValue = rmaAutoApproveMaxValue != null
                ? rmaAutoApproveMaxValue
                : new BigDecimal("100.00");
    }

    public boolean isBlindCycleCounts() {
        return blindCycleCounts;
    }

    public void setBlindCycleCounts(boolean blindCycleCounts) {
        this.blindCycleCounts = blindCycleCounts;
    }

    public BigDecimal getMaxAutoAdjustValue() {
        return maxAutoAdjustValue;
    }

    public void setMaxAutoAdjustValue(BigDecimal maxAutoAdjustValue) {
        this.maxAutoAdjustValue = maxAutoAdjustValue != null
                ? maxAutoAdjustValue
                : new BigDecimal("100.00");
    }

    public boolean isPredictiveReplenishmentEnabled() {
        return predictiveReplenishmentEnabled;
    }

    public void setPredictiveReplenishmentEnabled(boolean predictiveReplenishmentEnabled) {
        this.predictiveReplenishmentEnabled = predictiveReplenishmentEnabled;
    }

    public int getDesktopIdleTimeoutMinutes() {
        return desktopIdleTimeoutMinutes;
    }

    public void setDesktopIdleTimeoutMinutes(Integer desktopIdleTimeoutMinutes) {
        this.desktopIdleTimeoutMinutes = desktopIdleTimeoutMinutes != null
                ? desktopIdleTimeoutMinutes
                : 30;
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
        defaults.put("picking_wave_max_lines", 40);
        defaults.put("picking_wave_max_orders", 12);
        defaults.put("allow_over_receiving", false);
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
        defaults.put("pos_receipt_header", "");
        defaults.put("pos_receipt_footer", "");
        defaults.put("pos_default_currency", "USD");
        defaults.put("pos_require_blind_closeout", false);
        defaults.put("pos_enable_cfdi_invoicing", false);
        defaults.put("desktop_idle_timeout_minutes", 30);
        ts.setSettings(defaults);
        ts.setDesktopIdleTimeoutMinutes(30);
        return ts;
    }

    public String getPosReceiptHeader() {
        return stringSetting("pos_receipt_header", "");
    }

    public void setPosReceiptHeader(String posReceiptHeader) {
        settings.put("pos_receipt_header", posReceiptHeader != null ? posReceiptHeader : "");
    }

    public String getPosReceiptFooter() {
        return stringSetting("pos_receipt_footer", "");
    }

    public void setPosReceiptFooter(String posReceiptFooter) {
        settings.put("pos_receipt_footer", posReceiptFooter != null ? posReceiptFooter : "");
    }

    public String getPosDefaultCurrency() {
        return stringSetting("pos_default_currency", "USD");
    }

    public void setPosDefaultCurrency(String posDefaultCurrency) {
        settings.put("pos_default_currency", posDefaultCurrency != null ? posDefaultCurrency : "USD");
    }

    public Boolean getPosRequireBlindCloseout() {
        return boolSetting("pos_require_blind_closeout", false);
    }

    public void setPosRequireBlindCloseout(Boolean posRequireBlindCloseout) {
        settings.put("pos_require_blind_closeout", Boolean.TRUE.equals(posRequireBlindCloseout));
    }

    public Boolean getPosEnableCfdiInvoicing() {
        return boolSetting("pos_enable_cfdi_invoicing", false);
    }

    public void setPosEnableCfdiInvoicing(Boolean posEnableCfdiInvoicing) {
        settings.put("pos_enable_cfdi_invoicing", Boolean.TRUE.equals(posEnableCfdiInvoicing));
    }

    private String stringSetting(String key, String fallback) {
        Object raw = settings.get(key);
        if (raw == null) {
            return fallback;
        }
        String value = String.valueOf(raw);
        return value.isBlank() ? fallback : value;
    }

    private boolean boolSetting(String key, boolean fallback) {
        Object raw = settings.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }
}
