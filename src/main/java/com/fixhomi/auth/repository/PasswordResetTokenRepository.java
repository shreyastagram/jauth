package com.fixhomi.auth.repository;

import com.fixhomi.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for PasswordResetToken entity operations.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Find a valid (non-expired, non-used) token.
     */
    @Query("SELECT p FROM PasswordResetToken p WHERE p.token = :token " +
           "AND p.used = false AND p.expiresAt > :now")
    Optional<PasswordResetToken> findValidToken(@Param("token") String token, @Param("now") LocalDateTime now);

    /**
     * Find token by token string (regardless of status).
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Invalidate all existing reset tokens for a user.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.used = true WHERE p.userId = :userId AND p.used = false")
    int invalidateAllUserTokens(@Param("userId") Long userId);

    /**
     * Delete expired tokens (for cleanup job).
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Check if user has a recent token request (for rate limiting).
     */
    @Query("SELECT COUNT(p) FROM PasswordResetToken p WHERE p.userId = :userId AND p.createdAt > :since")
    long countRecentTokenRequests(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * Count recent requests by email (for rate limiting before user lookup).
     */
    @Query("SELECT COUNT(p) FROM PasswordResetToken p WHERE p.email = :email AND p.createdAt > :since")
    long countRecentRequestsByEmail(@Param("email") String email, @Param("since") LocalDateTime since);
}
