package com.invsys.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "users")
public class User extends TenantScopedEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "terminal_pin_hash", length = 64)
    private String terminalPinHash;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    /** Legacy mirror of {@link #corporateDepartment}. */
    @Column(length = 128)
    private String department;

    @Column(name = "corporate_department", length = 128)
    private String corporateDepartment;

    @Column(name = "timezone_preference", length = 64)
    private String timezonePreference;

    @Column(name = "locale_language", length = 16)
    private String localeLanguage;

    @Column(name = "assigned_warehouse_id")
    private UUID assignedWarehouseId;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    /** Legacy mirror of {@link #shiftScheduleType}. */
    @Column(name = "shift_schedule", length = 32)
    private String shiftSchedule;

    /** DAY | NIGHT | WEEKEND */
    @Column(name = "shift_schedule_type", length = 32)
    private String shiftScheduleType;

    @Column(name = "address_line1", length = 256)
    private String addressLine1;

    @Column(name = "address_line2", length = 256)
    private String addressLine2;

    @Column(name = "address_city", length = 128)
    private String addressCity;

    @Column(name = "address_region", length = 64)
    private String addressRegion;

    @Column(name = "address_postal_code", length = 32)
    private String addressPostalCode;

    @Column(name = "address_country", length = 2)
    private String addressCountry;

    @Column(length = 64)
    private String phone;

    /** COMPACT | COMFORTABLE | SPACIOUS */
    @Column(name = "ui_density_preference", length = 16)
    private String uiDensityPreference;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @JsonIgnore
    public String getTerminalPinHash() {
        return terminalPinHash;
    }

    public void setTerminalPinHash(String terminalPinHash) {
        this.terminalPinHash = terminalPinHash;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getDepartment() {
        return corporateDepartment != null ? corporateDepartment : department;
    }

    public void setDepartment(String department) {
        setCorporateDepartment(department);
    }

    public String getCorporateDepartment() {
        return corporateDepartment != null ? corporateDepartment : department;
    }

    public void setCorporateDepartment(String corporateDepartment) {
        String normalized = corporateDepartment == null || corporateDepartment.isBlank()
                ? null
                : corporateDepartment.trim();
        this.corporateDepartment = normalized;
        this.department = normalized;
    }

    public String getTimezonePreference() {
        return timezonePreference;
    }

    public void setTimezonePreference(String timezonePreference) {
        this.timezonePreference = timezonePreference;
    }

    public String getLocaleLanguage() {
        return localeLanguage;
    }

    public void setLocaleLanguage(String localeLanguage) {
        this.localeLanguage = localeLanguage;
    }

    public UUID getAssignedWarehouseId() {
        return assignedWarehouseId;
    }

    public void setAssignedWarehouseId(UUID assignedWarehouseId) {
        this.assignedWarehouseId = assignedWarehouseId;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public void setMfaEnabled(boolean mfaEnabled) {
        this.mfaEnabled = mfaEnabled;
    }

    public String getShiftSchedule() {
        return shiftScheduleType != null ? shiftScheduleType : shiftSchedule;
    }

    public void setShiftSchedule(String shiftSchedule) {
        setShiftScheduleType(shiftSchedule);
    }

    public String getShiftScheduleType() {
        return shiftScheduleType != null ? shiftScheduleType : shiftSchedule;
    }

    public void setShiftScheduleType(String shiftScheduleType) {
        String normalized = shiftScheduleType == null || shiftScheduleType.isBlank()
                ? null
                : shiftScheduleType.trim().toUpperCase();
        this.shiftScheduleType = normalized;
        this.shiftSchedule = normalized;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public void setAddressCity(String addressCity) {
        this.addressCity = addressCity;
    }

    public String getAddressRegion() {
        return addressRegion;
    }

    public void setAddressRegion(String addressRegion) {
        this.addressRegion = addressRegion;
    }

    public String getAddressPostalCode() {
        return addressPostalCode;
    }

    public void setAddressPostalCode(String addressPostalCode) {
        this.addressPostalCode = addressPostalCode;
    }

    public String getAddressCountry() {
        return addressCountry;
    }

    public void setAddressCountry(String addressCountry) {
        this.addressCountry = addressCountry;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUiDensityPreference() {
        return uiDensityPreference;
    }

    public void setUiDensityPreference(String uiDensityPreference) {
        this.uiDensityPreference = uiDensityPreference;
    }
}
