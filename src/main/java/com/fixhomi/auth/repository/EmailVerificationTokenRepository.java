package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for EmailVerificationToken entity operations.
 */
@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /**
     * Find a valid (non-expired, non-verified) token.
     */
    Optional<EmailVerificationToken> findByTokenAndVerifiedFalse(String token);

    /**
     * Find the latest valid token for a user.
     */
    @Query("SELECT e FROM EmailVerificationToken e WHERE e.userId = :userId " +
           "AND e.verified = false AND e.expiresAt > :now ORDER BY e.createdAt DESC LIMIT 1")
    Optional<EmailVerificationToken> findLatestValidTokenForUser(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now);

    /**
     * Invalidate all existing tokens for a user.
     */
    @Modifying
    @Query("UPDATE EmailVerificationToken e SET e.verified = true WHERE e.userId = :userId AND e.verified = false")
    int invalidateAllUserTokens(@Param("userId") Long userId);

    /**
     * Delete expired tokens (for cleanup job).
     */
    @Modifying
    @Query("DELETE FROM EmailVerificationToken e WHERE e.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Check if user has a recent token request (for rate limiting).
     */
    @Query("SELECT COUNT(e) FROM EmailVerificationToken e WHERE e.userId = :userId AND e.createdAt > :since")
    long countRecentTokenRequests(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
