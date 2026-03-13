package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for EmailOtp entity operations.
 */
@Repository
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    /**
     * Find the latest non-expired, non-verified OTP for an email.
     */
    @Query("SELECT e FROM EmailOtp e WHERE e.email = :email " +
           "AND e.verified = false AND e.expiresAt > :now ORDER BY e.createdAt DESC LIMIT 1")
    Optional<EmailOtp> findLatestValidOtpByEmail(
            @Param("email") String email,
            @Param("now") LocalDateTime now);

    /**
     * Invalidate all existing OTPs for a user's email.
     */
    @Modifying
    @Query("UPDATE EmailOtp e SET e.verified = true WHERE e.userId = :userId " +
           "AND e.email = :email AND e.verified = false")
    int invalidateAllUserOtps(@Param("userId") Long userId, @Param("email") String email);

    /**
     * Delete expired OTPs (for cleanup job).
     */
    @Modifying
    @Query("DELETE FROM EmailOtp e WHERE e.expiresAt < :now")
    int deleteExpiredOtps(@Param("now") LocalDateTime now);

    /**
     * Count recent OTP requests for rate limiting.
     */
    @Query("SELECT COUNT(e) FROM EmailOtp e WHERE e.email = :email " +
           "AND e.createdAt > :since")
    long countRecentOtpRequests(@Param("email") String email, @Param("since") LocalDateTime since);
}
