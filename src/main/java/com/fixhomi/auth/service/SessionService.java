package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.DeviceInfoRequest;
import com.fixhomi.auth.dto.SessionResponse;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.TrustedDevice;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.entity.UserSession;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.repository.TrustedDeviceRepository;
import com.fixhomi.auth.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing user sessions and trusted devices.
 * Provides multi-device session tracking and security features.
 */
@Service
public class SessionService {

    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private TrustedDeviceRepository trustedDeviceRepository;

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Create or update a session for a user on a device
     */
    @Transactional
    public UserSession createOrUpdateSession(User user, DeviceInfoRequest deviceInfo, RefreshToken refreshToken, String ipAddress) {
        logger.info("Creating/updating session for user {} on device {}", user.getEmail(), deviceInfo.getDeviceId());

        // Check for existing session on this device
        Optional<UserSession> existingSession = sessionRepository
                .findByUserAndDeviceIdAndIsActiveTrue(user, deviceInfo.getDeviceId());

        UserSession session;
        if (existingSession.isPresent()) {
            session = existingSession.get();
            session.setRefreshToken(refreshToken);
            session.updateLastActivity();
            logger.info("Updated existing session {} for device {}", session.getId(), deviceInfo.getDeviceId());
        } else {
            session = new UserSession(user, deviceInfo.getDeviceId());
            session.setRefreshToken(refreshToken);
            logger.info("Created new session for device {}", deviceInfo.getDeviceId());
        }

        // Update device info
        session.setDeviceName(deviceInfo.getDeviceName());
        session.setDeviceModel(deviceInfo.getDeviceModel());
        session.setPlatform(deviceInfo.getPlatform());
        session.setSystemVersion(deviceInfo.getSystemVersion());
        session.setAppVersion(deviceInfo.getAppVersion());
        session.setIpAddress(ipAddress);

        // Check if device is trusted
        boolean isTrusted = trustedDeviceRepository
                .existsByUserAndDeviceIdAndIsActiveTrue(user, deviceInfo.getDeviceId());
        session.setIsTrusted(isTrusted);

        return sessionRepository.save(session);
    }

    /**
     * Get all active sessions for a user
     */
    public List<SessionResponse> getActiveSessions(User user, String currentDeviceId) {
        List<UserSession> sessions = sessionRepository.findByUserAndIsActiveTrue(user);

        return sessions.stream()
                .map(session -> toSessionResponse(session, currentDeviceId))
                .collect(Collectors.toList());
    }

    /**
     * Convert UserSession entity to SessionResponse DTO
     */
    private SessionResponse toSessionResponse(UserSession session, String currentDeviceId) {
        return SessionResponse.builder()
                .id(session.getId())
                .deviceId(session.getDeviceId())
                .deviceName(session.getDeviceName())
                .deviceModel(session.getDeviceModel())
                .platform(session.getPlatform())
                .systemVersion(session.getSystemVersion())
                .appVersion(session.getAppVersion())
                .ipAddress(session.getIpAddress())
                .location(session.getLocation())
                .isTrusted(session.getIsTrusted())
                .isCurrentSession(session.getDeviceId() != null && 
                        session.getDeviceId().equals(currentDeviceId))
                .lastActivityAt(session.getLastActivityAt())
                .createdAt(session.getCreatedAt())
                .build();
    }

    /**
     * Revoke a specific session
     */
    @Transactional
    public void revokeSession(User user, Long sessionId) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new AuthenticationException("Session not found"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new AuthenticationException("Not authorized to revoke this session");
        }

        session.revoke();
        sessionRepository.save(session);
        logger.info("Revoked session {} for user {}", sessionId, user.getEmail());
    }

    /**
     * Revoke all sessions for a user except the current device
     */
    @Transactional
    public int revokeAllSessionsExceptDevice(User user, String exceptDeviceId) {
        int revokedCount = sessionRepository.revokeAllSessionsExceptDevice(user, exceptDeviceId);
        logger.info("Revoked {} sessions for user {} (except device {})", 
                revokedCount, user.getEmail(), exceptDeviceId);
        return revokedCount;
    }

    /**
     * Revoke all sessions for a user
     */
    @Transactional
    public int revokeAllSessions(User user) {
        int revokedCount = sessionRepository.revokeAllSessionsForUser(user);
        logger.info("Revoked all {} sessions for user {}", revokedCount, user.getEmail());
        return revokedCount;
    }

    /**
     * Update session activity timestamp
     */
    @Transactional
    public void updateSessionActivity(Long sessionId) {
        sessionRepository.updateLastActivity(sessionId, LocalDateTime.now());
    }

    /**
     * Get session by refresh token
     */
    public Optional<UserSession> getSessionByRefreshToken(Long refreshTokenId) {
        return sessionRepository.findByRefreshTokenId(refreshTokenId);
    }

    // ==================== DEVICE TRUST MANAGEMENT ====================

    /**
     * Trust a device for a user.
     * Uses upsert pattern to handle re-trusting previously untrusted devices.
     */
    @Transactional
    public TrustedDevice trustDevice(User user, DeviceInfoRequest deviceInfo) {
        logger.info("Trusting device {} for user {}", deviceInfo.getDeviceId(), user.getEmail());

        // First check if device exists (active OR inactive) to handle re-trusting
        Optional<TrustedDevice> existingAny = trustedDeviceRepository
                .findByUserAndDeviceId(user, deviceInfo.getDeviceId());

        TrustedDevice trustedDevice;
        
        if (existingAny.isPresent()) {
            // Device exists (either active or was previously untrusted)
            trustedDevice = existingAny.get();
            trustedDevice.setIsActive(true); // Re-activate if it was untrusted
            trustedDevice.updateLastUsed();
            if (deviceInfo.getCustomName() != null) {
                trustedDevice.setCustomName(deviceInfo.getCustomName());
            }
            // Update device info in case it changed
            if (deviceInfo.getDeviceName() != null) {
                trustedDevice.setDeviceName(deviceInfo.getDeviceName());
            }
            if (deviceInfo.getDeviceModel() != null) {
                trustedDevice.setDeviceModel(deviceInfo.getDeviceModel());
            }
            if (deviceInfo.getPlatform() != null) {
                trustedDevice.setPlatform(deviceInfo.getPlatform());
            }
            if (deviceInfo.getSystemVersion() != null) {
                trustedDevice.setSystemVersion(deviceInfo.getSystemVersion());
            }
            if (deviceInfo.getAppVersion() != null) {
                trustedDevice.setAppVersion(deviceInfo.getAppVersion());
            }
            logger.info("Re-trusting existing device {} for user {}", deviceInfo.getDeviceId(), user.getEmail());
        } else {
            // Create new trusted device
            trustedDevice = new TrustedDevice(user, deviceInfo.getDeviceId());
            trustedDevice.setDeviceName(deviceInfo.getDeviceName());
            trustedDevice.setCustomName(deviceInfo.getCustomName());
            trustedDevice.setDeviceModel(deviceInfo.getDeviceModel());
            trustedDevice.setPlatform(deviceInfo.getPlatform());
            trustedDevice.setSystemVersion(deviceInfo.getSystemVersion());
            trustedDevice.setAppVersion(deviceInfo.getAppVersion());
            logger.info("Creating new trusted device {} for user {}", deviceInfo.getDeviceId(), user.getEmail());
        }

        TrustedDevice saved = trustedDeviceRepository.save(trustedDevice);

        // Also update the session's trust status
        sessionRepository.findByUserAndDeviceIdAndIsActiveTrue(user, deviceInfo.getDeviceId())
                .ifPresent(session -> {
                    session.setIsTrusted(true);
                    sessionRepository.save(session);
                });

        logger.info("Device {} trusted for user {}", deviceInfo.getDeviceId(), user.getEmail());
        return saved;
    }

    /**
     * Get all trusted devices for a user
     */
    public List<TrustedDevice> getTrustedDevices(User user) {
        return trustedDeviceRepository.findByUserAndIsActiveTrueOrderByLastUsedAtDesc(user);
    }

    /**
     * Check if a device is trusted
     */
    public boolean isDeviceTrusted(User user, String deviceId) {
        return trustedDeviceRepository.existsByUserAndDeviceIdAndIsActiveTrue(user, deviceId);
    }

    /**
     * Untrust a device
     */
    @Transactional
    public void untrustDevice(User user, String deviceId) {
        Optional<TrustedDevice> device = trustedDeviceRepository
                .findByUserAndDeviceIdAndIsActiveTrue(user, deviceId);

        if (device.isPresent()) {
            device.get().revoke();
            trustedDeviceRepository.save(device.get());

            // Also update the session's trust status
            sessionRepository.findByUserAndDeviceIdAndIsActiveTrue(user, deviceId)
                    .ifPresent(session -> {
                        session.setIsTrusted(false);
                        sessionRepository.save(session);
                    });

            logger.info("Device {} untrusted for user {}", deviceId, user.getEmail());
        }
    }

    /**
     * Untrust all devices for a user
     */
    @Transactional
    public int untrustAllDevices(User user) {
        return trustedDeviceRepository.revokeAllTrustedDevicesForUser(user);
    }

    // ==================== CLEANUP ====================

    /**
     * Clean up old inactive sessions (for scheduled job)
     */
    @Transactional
    public int cleanupOldSessions(int daysOld) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysOld);
        int deleted = sessionRepository.deleteInactiveSessionsOlderThan(threshold);
        logger.info("Cleaned up {} old sessions", deleted);
        return deleted;
    }
}
