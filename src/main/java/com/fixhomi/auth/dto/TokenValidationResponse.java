package com.fixhomi.auth.dto;

import com.fixhomi.auth.entity.Role;

/**
 * Response DTO for JWT validation endpoint.
 * Returns token claims for Node.js services to use.
 */
public class TokenValidationResponse {

    private boolean valid;
    private Long userId;
    private String email;
    private Role role;
    private String tokenType;
    private Long issuedAt;      // Unix timestamp (seconds)
    private Long expiresAt;     // Unix timestamp (seconds)

    // Constructors
    public TokenValidationResponse() {
    }

    public TokenValidationResponse(boolean valid, Long userId, String email, Role role, 
                                   String tokenType, Long issuedAt, Long expiresAt) {
        this.valid = valid;
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.tokenType = tokenType;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    // Static factory for invalid token response
    public static TokenValidationResponse invalid() {
        TokenValidationResponse response = new TokenValidationResponse();
        response.setValid(false);
        return response;
    }

    // Getters and Setters
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Long issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
