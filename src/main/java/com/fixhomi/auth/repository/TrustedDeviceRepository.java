package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.TrustedDevice;
import com.fixhomi.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TrustedDevice entity.
 * Manages trusted devices for enhanced security.
 */
@Repository
public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {

    /**
     * Find all active trusted devices for a user
     */
    List<TrustedDevice> findByUserAndIsActiveTrueOrderByLastUsedAtDesc(User user);

    /**
     * Find a specific trusted device
     */
    Optional<TrustedDevice> findByUserAndDeviceIdAndIsActiveTrue(User user, String deviceId);

    /**
     * Find a device by user and deviceId (regardless of active status).
     * Used for re-trusting previously untrusted devices.
     */
    Optional<TrustedDevice> findByUserAndDeviceId(User user, String deviceId);

    /**
     * Check if device is trusted
     */
    boolean existsByUserAndDeviceIdAndIsActiveTrue(User user, String deviceId);

    /**
     * Count trusted devices for a user
     */
    long countByUserAndIsActiveTrue(User user);

    /**
     * Revoke all trusted devices for a user
     */
    @Modifying
    @Query("UPDATE TrustedDevice d SET d.isActive = false WHERE d.user = :user")
    int revokeAllTrustedDevicesForUser(@Param("user") User user);

    /**
     * Update last used timestamp
     */
    @Modifying
    @Query("UPDATE TrustedDevice d SET d.lastUsedAt = CURRENT_TIMESTAMP WHERE d.user = :user AND d.deviceId = :deviceId")
    int updateLastUsed(@Param("user") User user, @Param("deviceId") String deviceId);
}
