package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for PasswordResetOtp entity operations.
 * Used for OTP-based password reset.
 */
@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    /**
     * Find the latest valid (non-expired, non-used) OTP for a phone number.
     */
    @Query("SELECT p FROM PasswordResetOtp p WHERE p.phoneNumber = :phoneNumber " +
           "AND p.used = false AND p.expiresAt > :now ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PasswordResetOtp> findLatestValidOtp(
            @Param("phoneNumber") String phoneNumber,
            @Param("now") LocalDateTime now);

    /**
     * Invalidate all existing password reset OTPs for a phone number.
     */
    @Modifying
    @Query("UPDATE PasswordResetOtp p SET p.used = true WHERE p.phoneNumber = :phoneNumber AND p.used = false")
    int invalidateAllOtpsForPhone(@Param("phoneNumber") String phoneNumber);

    /**
     * Invalidate all existing password reset OTPs for a user.
     */
    @Modifying
    @Query("UPDATE PasswordResetOtp p SET p.used = true WHERE p.userId = :userId AND p.used = false")
    int invalidateAllOtpsForUser(@Param("userId") Long userId);

    /**
     * Count recent OTP requests for rate limiting.
     */
    @Query("SELECT COUNT(p) FROM PasswordResetOtp p WHERE p.phoneNumber = :phoneNumber AND p.createdAt > :since")
    long countRecentOtpRequests(@Param("phoneNumber") String phoneNumber, @Param("since") LocalDateTime since);

    /**
     * Delete expired OTPs (for cleanup job).
     */
    @Modifying
    @Query("DELETE FROM PasswordResetOtp p WHERE p.expiresAt < :now")
    int deleteExpiredOtps(@Param("now") LocalDateTime now);
}
