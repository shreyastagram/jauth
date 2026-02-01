package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.DeviceInfoRequest;
import com.fixhomi.auth.dto.RevokeAllSessionsRequest;
import com.fixhomi.auth.dto.SessionResponse;
import com.fixhomi.auth.entity.TrustedDevice;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for session management endpoints.
 * Provides multi-device session tracking and trusted device management.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Session Management", description = "Endpoints for managing user sessions and trusted devices")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private UserRepository userRepository;

    // ==================== SESSION ENDPOINTS ====================

    /**
     * Get all active sessions for the current user
     */
    @GetMapping("/sessions")
    @Operation(summary = "Get active sessions", description = "Returns all active sessions for the authenticated user")
    public ResponseEntity<Map<String, Object>> getActiveSessions(
            @RequestHeader(value = "X-Device-Id", required = false) String currentDeviceId) {
        
        User user = getCurrentUser();
        logger.info("Getting active sessions for user: {}", user.getEmail());

        List<SessionResponse> sessions = sessionService.getActiveSessions(user, currentDeviceId);

        Map<String, Object> response = new HashMap<>();
        response.put("sessions", sessions);
        response.put("count", sessions.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Revoke a specific session
     */
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Revoke session", description = "Revokes a specific session by ID")
    public ResponseEntity<Map<String, Object>> revokeSession(@PathVariable Long sessionId) {
        User user = getCurrentUser();
        logger.info("Revoking session {} for user: {}", sessionId, user.getEmail());

        sessionService.revokeSession(user, sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Session revoked successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Revoke all sessions except the current device
     */
    @PostMapping("/sessions/revoke-all")
    @Operation(summary = "Revoke all other sessions", description = "Revokes all sessions except the current device")
    public ResponseEntity<Map<String, Object>> revokeAllOtherSessions(
            @RequestBody(required = false) RevokeAllSessionsRequest request) {
        
        User user = getCurrentUser();
        String exceptDeviceId = request != null ? request.getExceptDeviceId() : null;
        
        logger.info("Revoking all sessions for user: {} (except device: {})", 
                user.getEmail(), exceptDeviceId);

        int revokedCount;
        if (exceptDeviceId != null && !exceptDeviceId.isEmpty()) {
            revokedCount = sessionService.revokeAllSessionsExceptDevice(user, exceptDeviceId);
        } else {
            revokedCount = sessionService.revokeAllSessions(user);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Sessions revoked successfully");
        response.put("revokedCount", revokedCount);

        return ResponseEntity.ok(response);
    }

    // ==================== TOKEN VALIDATION ====================

    /**
     * Validate the current access token
     * Returns user info if token is valid
     */
    @GetMapping("/validate")
    @Operation(summary = "Validate token", description = "Validates the current access token and returns user info")
    public ResponseEntity<Map<String, Object>> validateToken() {
        User user = getCurrentUser();
        logger.info("Token validated for user: {}", user.getEmail());

        Map<String, Object> response = new HashMap<>();
        response.put("valid", true);
        response.put("userId", user.getId());
        response.put("email", user.getEmail());
        response.put("role", user.getRole().name());
        response.put("isEmailVerified", user.getIsEmailVerified());
        response.put("isPhoneVerified", user.getIsPhoneVerified());

        return ResponseEntity.ok(response);
    }

    // ==================== TRUSTED DEVICE ENDPOINTS ====================

    /**
     * Trust the current device
     */
    @PostMapping("/devices/trust")
    @Operation(summary = "Trust device", description = "Marks the current device as trusted")
    public ResponseEntity<Map<String, Object>> trustDevice(
            @Valid @RequestBody DeviceInfoRequest deviceInfo) {
        
        User user = getCurrentUser();
        logger.info("Trusting device {} for user: {}", deviceInfo.getDeviceId(), user.getEmail());

        TrustedDevice trusted = sessionService.trustDevice(user, deviceInfo);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Device trusted successfully");
        response.put("deviceId", trusted.getDeviceId());
        response.put("trustedAt", trusted.getTrustedAt());

        return ResponseEntity.ok(response);
    }

    /**
     * Get all trusted devices
     */
    @GetMapping("/devices/trust")
    @Operation(summary = "Get trusted devices", description = "Returns all trusted devices for the user")
    public ResponseEntity<Map<String, Object>> getTrustedDevices() {
        User user = getCurrentUser();
        logger.info("Getting trusted devices for user: {}", user.getEmail());

        List<TrustedDevice> devices = sessionService.getTrustedDevices(user);

        List<Map<String, Object>> deviceList = devices.stream()
                .map(this::toDeviceMap)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("devices", deviceList);
        response.put("count", devices.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Untrust a device
     */
    @DeleteMapping("/devices/trust/{deviceId}")
    @Operation(summary = "Untrust device", description = "Removes trust from a specific device")
    public ResponseEntity<Map<String, Object>> untrustDevice(@PathVariable String deviceId) {
        User user = getCurrentUser();
        logger.info("Untrusting device {} for user: {}", deviceId, user.getEmail());

        sessionService.untrustDevice(user, deviceId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Device trust removed successfully");

        return ResponseEntity.ok(response);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get the current authenticated user
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AuthenticationException("Not authenticated");
        }

        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("User not found"));
    }

    /**
     * Convert TrustedDevice to Map for response
     */
    private Map<String, Object> toDeviceMap(TrustedDevice device) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", device.getId());
        map.put("deviceId", device.getDeviceId());
        map.put("deviceName", device.getDeviceName() != null ? device.getDeviceName() : device.getCustomName());
        map.put("customName", device.getCustomName());
        map.put("deviceModel", device.getDeviceModel());
        map.put("platform", device.getPlatform());
        map.put("systemVersion", device.getSystemVersion());
        map.put("appVersion", device.getAppVersion());
        map.put("trustedAt", device.getTrustedAt());
        map.put("lastUsedAt", device.getLastUsedAt());
        return map;
    }
}
