package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.LoginRequest;
import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.dto.LogoutRequest;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.RefreshTokenRequest;
import com.fixhomi.auth.dto.RegisterRequest;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.security.JwtService;
import com.fixhomi.auth.service.AuthService;
import com.fixhomi.auth.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtService jwtService;

    /**
     * Login endpoint.
     * POST /api/auth/login
     *
     * @param loginRequest login credentials
     * @return JWT token and user info
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Registration endpoint.
     * POST /api/auth/register
     *
     * @param registerRequest registration details
     * @return JWT token and user info
     */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        LoginResponse response = authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Refresh access token endpoint.
     * POST /api/auth/refresh
     *
     * Uses refresh token to generate new access token and rotated refresh token.
     *
     * @param request refresh token
     * @return new access token and refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        // Rotate refresh token (validates and revokes old, creates new)
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
        User user = newRefreshToken.getUser();

        // Generate new access token
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                newRefreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds()
        ));
    }

    /**
     * Logout endpoint.
     * POST /api/auth/logout
     *
     * Revokes the provided refresh token.
     *
     * @param request refresh token to revoke
     * @return success message
     */
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody LogoutRequest request) {
        refreshTokenService.revokeToken(request.getRefreshToken());
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    /**
     * Health check endpoint.
     * GET /api/health
     *
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "Auth service is running"));
    }

    /**
     * Health response DTO.
     */
    public static class HealthResponse {
        private String status;
        private String message;

        public HealthResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
