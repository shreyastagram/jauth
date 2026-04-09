package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.AppleEmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for AppleEmailOtp entity operations.
 */
@Repository
public interface AppleEmailOtpRepository extends JpaRepository<AppleEmailOtp, Long> {

    /**
     * Find the latest unverified OTP for an Apple user + email combination.
     */
    Optional<AppleEmailOtp> findTopByAppleUserIdAndEmailAndVerifiedFalseOrderByCreatedAtDesc(
            String appleUserId, String email);

    /**
     * Delete all OTP records for an Apple user (cleanup after successful verification).
     */
    @Modifying
    void deleteByAppleUserId(String appleUserId);

    /**
     * Count recent OTP requests for rate limiting.
     */
    long countByAppleUserIdAndCreatedAtAfter(String appleUserId, LocalDateTime after);

    /**
     * Delete expired OTPs (for cleanup job).
     */
    @Modifying
    @Query("DELETE FROM AppleEmailOtp e WHERE e.expiresAt < :now")
    int deleteExpiredOtps(@Param("now") LocalDateTime now);
}
