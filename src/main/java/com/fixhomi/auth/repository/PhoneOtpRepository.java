package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.PhoneOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for PhoneOtp entity operations.
 */
@Repository
public interface PhoneOtpRepository extends JpaRepository<PhoneOtp, Long> {

    /**
     * Find the latest non-expired, non-verified OTP for a user.
     */
    @Query("SELECT p FROM PhoneOtp p WHERE p.userId = :userId AND p.phoneNumber = :phoneNumber " +
           "AND p.verified = false AND p.expiresAt > :now ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PhoneOtp> findLatestValidOtp(
            @Param("userId") Long userId,
            @Param("phoneNumber") String phoneNumber,
            @Param("now") LocalDateTime now);

    /**
     * Find OTP by phone number (latest valid one).
     */
    @Query("SELECT p FROM PhoneOtp p WHERE p.phoneNumber = :phoneNumber " +
           "AND p.verified = false AND p.expiresAt > :now ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PhoneOtp> findLatestValidOtpByPhone(
            @Param("phoneNumber") String phoneNumber,
            @Param("now") LocalDateTime now);

    /**
     * Invalidate all existing OTPs for a user's phone number.
     */
    @Modifying
    @Query("UPDATE PhoneOtp p SET p.verified = true WHERE p.userId = :userId " +
           "AND p.phoneNumber = :phoneNumber AND p.verified = false")
    int invalidateAllUserOtps(@Param("userId") Long userId, @Param("phoneNumber") String phoneNumber);

    /**
     * Delete expired OTPs (for cleanup job).
     */
    @Modifying
    @Query("DELETE FROM PhoneOtp p WHERE p.expiresAt < :now")
    int deleteExpiredOtps(@Param("now") LocalDateTime now);

    /**
     * Count recent OTP requests for rate limiting.
     */
    @Query("SELECT COUNT(p) FROM PhoneOtp p WHERE p.phoneNumber = :phoneNumber " +
           "AND p.createdAt > :since")
    long countRecentOtpRequests(@Param("phoneNumber") String phoneNumber, @Param("since") LocalDateTime since);
}
