package com.fixhomi.auth.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing a trusted device.
 * Trusted devices may have extended session durations and reduced security prompts.
 */
@Entity
@Table(name = "trusted_devices", indexes = {
    @Index(name = "idx_trusted_device_user_id", columnList = "user_id"),
    @Index(name = "idx_trusted_device_device_id", columnList = "device_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_device", columnNames = {"user_id", "device_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "custom_name", length = 255)
    private String customName;

    @Column(name = "device_model", length = 255)
    private String deviceModel;

    @Column(name = "platform", length = 50)
    private String platform;

    @Column(name = "system_version", length = 50)
    private String systemVersion;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreatedDate
    @Column(name = "trusted_at", nullable = false, updatable = false)
    private LocalDateTime trustedAt;

    // Constructors
    public TrustedDevice() {
    }

    public TrustedDevice(User user, String deviceId) {
        this.user = user;
        this.deviceId = deviceId;
        this.lastUsedAt = LocalDateTime.now();
    }

    // Business methods
    public void updateLastUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }

    public void revoke() {
        this.isActive = false;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getSystemVersion() {
        return systemVersion;
    }

    public void setSystemVersion(String systemVersion) {
        this.systemVersion = systemVersion;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getTrustedAt() {
        return trustedAt;
    }

    public void setTrustedAt(LocalDateTime trustedAt) {
        this.trustedAt = trustedAt;
    }
}
