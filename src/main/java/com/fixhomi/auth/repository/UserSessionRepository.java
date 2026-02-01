package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for UserSession entity.
 * Provides methods for session management and multi-device tracking.
 */
@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /**
     * Find all active sessions for a user
     */
    List<UserSession> findByUserAndIsActiveTrue(User user);

    /**
     * Find all sessions for a user (including inactive)
     */
    List<UserSession> findByUserOrderByLastActivityAtDesc(User user);

    /**
     * Find active session by device ID
     */
    Optional<UserSession> findByUserAndDeviceIdAndIsActiveTrue(User user, String deviceId);

    /**
     * Find session by refresh token ID
     */
    Optional<UserSession> findByRefreshTokenId(Long refreshTokenId);

    /**
     * Count active sessions for a user
     */
    long countByUserAndIsActiveTrue(User user);

    /**
     * Revoke all sessions for a user
     */
    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false WHERE s.user = :user")
    int revokeAllSessionsForUser(@Param("user") User user);

    /**
     * Revoke all sessions for a user except one device
     */
    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false WHERE s.user = :user AND s.deviceId != :deviceId")
    int revokeAllSessionsExceptDevice(@Param("user") User user, @Param("deviceId") String deviceId);

    /**
     * Delete expired/inactive sessions older than a threshold
     */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.isActive = false AND s.updatedAt < :threshold")
    int deleteInactiveSessionsOlderThan(@Param("threshold") LocalDateTime threshold);

    /**
     * Update last activity timestamp
     */
    @Modifying
    @Query("UPDATE UserSession s SET s.lastActivityAt = :timestamp WHERE s.id = :sessionId")
    int updateLastActivity(@Param("sessionId") Long sessionId, @Param("timestamp") LocalDateTime timestamp);

    /**
     * Check if a device is trusted for a user
     */
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM UserSession s WHERE s.user = :user AND s.deviceId = :deviceId AND s.isTrusted = true AND s.isActive = true")
    boolean isDeviceTrusted(@Param("user") User user, @Param("deviceId") String deviceId);
}
