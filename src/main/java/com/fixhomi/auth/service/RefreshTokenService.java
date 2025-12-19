package com.fixhomi.auth.service;

import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.repository.RefreshTokenRepository;
import com.fixhomi.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for managing refresh tokens.
 * Handles creation, validation, rotation, and revocation of refresh tokens.
 */
@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${jwt.refresh-token.expiration.days:7}")
    private int refreshTokenExpirationDays;

    /**
     * Create a new refresh token for a user.
     *
     * @param user the user to create the token for
     * @return the created refresh token
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        String tokenValue = generateTokenValue();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(refreshTokenExpirationDays);

        RefreshToken refreshToken = new RefreshToken(tokenValue, user, expiresAt);
        refreshToken = refreshTokenRepository.save(refreshToken);

        logger.debug("Created refresh token for user: {} (expires: {})", user.getEmail(), expiresAt);
        return refreshToken;
    }

    /**
     * Create a new refresh token for a user by ID.
     *
     * @param userId the user ID
     * @return the created refresh token
     */
    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));
        return createRefreshToken(user);
    }

    /**
     * Validate and rotate a refresh token.
     * Returns a new refresh token and revokes the old one.
     *
     * @param tokenValue the refresh token string
     * @return the new refresh token after rotation
     * @throws AuthenticationException if token is invalid, expired, or user is disabled
     */
    @Transactional
    public RefreshToken rotateRefreshToken(String tokenValue) {
        RefreshToken oldToken = refreshTokenRepository.findValidToken(tokenValue, LocalDateTime.now())
                .orElseThrow(() -> new AuthenticationException("Invalid or expired refresh token"));

        User user = oldToken.getUser();

        // Check if user is still active
        if (!user.getIsActive()) {
            // Revoke all tokens for disabled user
            revokeAllUserTokens(user.getId());
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Revoke the old token
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        // Create new token
        RefreshToken newToken = createRefreshToken(user);

        logger.info("Rotated refresh token for user: {}", user.getEmail());
        return newToken;
    }

    /**
     * Revoke a specific refresh token.
     *
     * @param tokenValue the refresh token string
     * @return true if token was found and revoked, false otherwise
     */
    @Transactional
    public boolean revokeToken(String tokenValue) {
        return refreshTokenRepository.findByToken(tokenValue)
                .map(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    logger.info("Revoked refresh token for user: {}", token.getUser().getEmail());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Revoke all refresh tokens for a user.
     * Called when user is disabled or changes password.
     *
     * @param userId the user ID
     * @return number of tokens revoked
     */
    @Transactional
    public int revokeAllUserTokens(Long userId) {
        int count = refreshTokenRepository.revokeAllByUserId(userId);
        logger.info("Revoked {} refresh tokens for user ID: {}", count, userId);
        return count;
    }

    /**
     * Find a valid refresh token and its associated user.
     *
     * @param tokenValue the refresh token string
     * @return the refresh token if valid
     * @throws AuthenticationException if token is invalid or expired
     */
    @Transactional(readOnly = true)
    public RefreshToken findValidToken(String tokenValue) {
        return refreshTokenRepository.findValidToken(tokenValue, LocalDateTime.now())
                .orElseThrow(() -> new AuthenticationException("Invalid or expired refresh token"));
    }

    /**
     * Get the expiration time in days.
     *
     * @return expiration time in days
     */
    public int getExpirationDays() {
        return refreshTokenExpirationDays;
    }

    /**
     * Generate a secure random token value.
     *
     * @return a UUID-based token string
     */
    private String generateTokenValue() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString().replace("-", "");
    }
}
