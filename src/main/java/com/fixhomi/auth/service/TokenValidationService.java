package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.TokenValidationResponse;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for JWT token validation operations.
 * Used by external services (Node.js) to validate tokens.
 */
@Service
public class TokenValidationService {

    private static final Logger logger = LoggerFactory.getLogger(TokenValidationService.class);

    @Autowired
    private JwtService jwtService;

    /**
     * Validate a JWT token and return its claims.
     * This does NOT issue new tokens - only validates existing ones.
     *
     * @param token JWT token to validate
     * @return TokenValidationResponse with claims if valid
     */
    public TokenValidationResponse validateToken(String token) {
        if (token == null || token.isBlank()) {
            logger.warn("Token validation attempted with empty token");
            return TokenValidationResponse.invalid();
        }

        // Remove "Bearer " prefix if present
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (!jwtService.validateToken(token)) {
            logger.debug("Token validation failed");
            return TokenValidationResponse.invalid();
        }

        try {
            Claims claims = jwtService.getClaimsFromToken(token);

            Long userId = claims.get("userId", Long.class);
            String email = claims.getSubject();
            String roleName = claims.get("role", String.class);
            Role role = Role.valueOf(roleName);
            String tokenType = claims.get("tokenType", String.class);
            Long issuedAt = claims.getIssuedAt().getTime() / 1000; // Convert to Unix timestamp
            Long expiresAt = claims.getExpiration().getTime() / 1000; // Convert to Unix timestamp

            logger.debug("Token validated successfully for user: {} (role: {})", email, role);

            return new TokenValidationResponse(
                    true,
                    userId,
                    email,
                    role,
                    tokenType,
                    issuedAt,
                    expiresAt
            );

        } catch (Exception ex) {
            logger.error("Error extracting claims from token: {}", ex.getMessage());
            return TokenValidationResponse.invalid();
        }
    }
}
