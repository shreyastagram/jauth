package com.fixhomi.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * DTO for returning session information to clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionResponse {

    private Long id;
    private String sessionId; // For backwards compatibility
    private String deviceId;
    private String deviceName;
    private String deviceModel;
    private String platform;
    private String systemVersion;
    private String appVersion;
    private String ipAddress;
    private String location;
    private Boolean isTrusted;
    private Boolean isCurrentSession;
    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;

    // Constructors
    public SessionResponse() {
    }

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SessionResponse response = new SessionResponse();

        public Builder id(Long id) {
            response.id = id;
            response.sessionId = String.valueOf(id);
            return this;
        }

        public Builder deviceId(String deviceId) {
            response.deviceId = deviceId;
            return this;
        }

        public Builder deviceName(String deviceName) {
            response.deviceName = deviceName;
            return this;
        }

        public Builder deviceModel(String deviceModel) {
            response.deviceModel = deviceModel;
            return this;
        }

        public Builder platform(String platform) {
            response.platform = platform;
            return this;
        }

        public Builder systemVersion(String systemVersion) {
            response.systemVersion = systemVersion;
            return this;
        }

        public Builder appVersion(String appVersion) {
            response.appVersion = appVersion;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            response.ipAddress = ipAddress;
            return this;
        }

        public Builder location(String location) {
            response.location = location;
            return this;
        }

        public Builder isTrusted(Boolean isTrusted) {
            response.isTrusted = isTrusted;
            return this;
        }

        public Builder isCurrentSession(Boolean isCurrentSession) {
            response.isCurrentSession = isCurrentSession;
            return this;
        }

        public Builder lastActivityAt(LocalDateTime lastActivityAt) {
            response.lastActivityAt = lastActivityAt;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            response.createdAt = createdAt;
            return this;
        }

        public SessionResponse build() {
            return response;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Boolean getIsTrusted() {
        return isTrusted;
    }

    public void setIsTrusted(Boolean isTrusted) {
        this.isTrusted = isTrusted;
    }

    public Boolean getIsCurrentSession() {
        return isCurrentSession;
    }

    public void setIsCurrentSession(Boolean isCurrentSession) {
        this.isCurrentSession = isCurrentSession;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
